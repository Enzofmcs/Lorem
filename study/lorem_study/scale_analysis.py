"""On-disk rating cells, grouped uncertainty, frozen-model CDF mixtures and t50."""

import argparse
import csv
import math
from pathlib import Path
import sqlite3
import time

import numpy as np
from scipy.special import ndtr

from .aft import cdf, locations, predict_aft
from .baseline import expected_d012_minutes, km_quantile
from .io import StudyError, check_version, digest, read_json, write_json
from .scale_prepare import file_sha


def validate_plan(plan):
    if plan.get("version") != "scale-t50-v1":
        raise StudyError("Unsupported scale plan")
    for field in ("old_rating_edges", "problem_rating_edges"):
        edges = plan.get(field)
        if (not isinstance(edges,list) or len(edges)<2 or
                any(type(v) is not int for v in edges) or edges != sorted(set(edges))):
            raise StudyError("Rating edges must be strictly increasing integers")
    for floors in [plan["support"],*plan["support_sensitivity"]]:
        if any(type(floors.get(k)) is not int or floors[k]<1 for k in ("attempts","events","groups")):
            raise StudyError("Invalid support floors")
    boot=plan["bootstrap"]
    if type(boot["replicates"]) is not int or boot["replicates"]<2 or boot["confidence"]!=.95:
        raise StudyError("Unsupported bootstrap plan")
    if boot["missing_policy"]!="any missing replicate => interval unavailable":
        raise StudyError("Missing bootstrap replicates cannot be silently dropped")
    return plan


def weighted_km(histogram):
    """Histogram rows (group, seconds, event, multiplicity); same ties as baseline."""
    counts={}
    for _,seconds,event,count in histogram:
        if (not math.isfinite(seconds) or seconds<=0 or event not in (0,1) or
                type(count) is not int or count<1):
            raise StudyError("Invalid weighted KM observation")
        values=counts.setdefault(seconds,[0,0])
        values[0 if event else 1]+=count
    n=sum(sum(v) for v in counts.values())
    risk,survival=n,1.
    table=[]
    median=None
    for seconds,(events,censored) in sorted(counts.items()):
        survival*=1-events/risk
        table.append({"seconds":seconds,"events":events,"censored":censored,"at_risk":risk,"survival":survival})
        if median is None and 1-survival>=.5-1e-12:
            median=seconds/60
        risk-=events+censored
    return {"n":n,"events":sum(v[0] for v in counts.values()),"t50_minutes":median,
            "max_followup_seconds":max(counts,default=0),"table":table}


def group_matrices(histogram):
    groups=sorted({r[0] for r in histogram})
    times=sorted({r[1] for r in histogram})
    gi,ti={g:i for i,g in enumerate(groups)},{t:i for i,t in enumerate(times)}
    events=np.zeros((len(groups),len(times)),dtype=float)
    totals=np.zeros_like(events)
    for g,t,event,count in histogram:
        totals[gi[g],ti[t]]+=count
        events[gi[g],ti[t]]+=count*event
    return groups,np.asarray(times),events,totals


def resampled_medians(events,totals,times,weights):
    e=weights@events
    exits=weights@totals
    risk=exits.sum(axis=1)[:,None]-np.cumsum(exits,axis=1)+exits
    hazard=np.divide(e,risk,out=np.zeros_like(e),where=risk>0)
    survival=np.cumprod(1-hazard,axis=1)
    crossed=survival<=.5+1e-12
    if len(times)==0:
        return [None]*len(weights)
    first=np.argmax(crossed,axis=1)
    return [float(times[i]/60) if has else None for i,has in zip(first,crossed.any(axis=1))]


def grouped_uncertainty(histogram,all_groups,draws,confidence=.95):
    groups,times,events,totals=group_matrices(histogram)
    if not groups:
        values=[None]*len(draws)
    else:
        positions={g:i for i,g in enumerate(all_groups)}
        values=resampled_medians(events,totals,times,draws[:,[positions[g] for g in groups]])
    missing=sum(v is None for v in values)
    alpha=(1-confidence)/2
    interval=(np.quantile(values,[alpha,1-alpha]).tolist() if not missing and values else None)
    by_group={}
    leave_out={}
    if groups:
        identity=np.eye(len(groups))
        individual=resampled_medians(events,totals,times,identity)
        leave=resampled_medians(events,totals,times,1-identity)
        by_group={str(g):v for g,v in zip(groups,individual)}
        leave_out={str(g):v for g,v in zip(groups,leave)}
    return {"ci95_minutes":interval,"missing_replicates":missing,"replicates":len(values),
            "unavailable_fraction":missing/len(values) if values else None,
            "reason":"missing_quantile_in_replicates" if missing else None,
            "replicate_t50_minutes":values,"by_group_t50_minutes":by_group,
            "leave_one_group_out_t50_minutes":leave_out,
            "kind":"whole-group sampling interval; model parameters fixed; repeated people may couple groups"}


def _step_values(curve,times):
    knots=np.asarray([r["seconds"]/60 for r in curve["table"]])
    cdf=np.asarray([1-r["survival"] for r in curve["table"]])
    index=np.searchsorted(knots,times,side="right")-1
    return np.where(index>=0,cdf[np.maximum(index,0)],0.)


class FrozenModels:
    """No fitting. Domain and delta-bin availability are explicit common support."""

    def __init__(self, directory, inventory):
        self.artifacts={}
        self.hashes={}
        if len(inventory)!=2 or {item["kind"] for item in inventory}!={"baseline","aft"}:
            raise StudyError("Inventory must identify exactly one baseline and one AFT artifact")
        for item in inventory:
            path=Path(directory)/Path(item["path"]).name
            if not path.exists():
                raise StudyError(f"Frozen model unavailable: {path.name}")
            if file_sha(path)!=item["sha256"]:
                raise StudyError("Frozen model file checksum mismatch")
            artifact=read_json(path)
            check_version(artifact)
            if digest(artifact)!=item["canonical_sha256"]:
                raise StudyError("Frozen model canonical checksum mismatch")
            if artifact.get("protocol_version")!="sequential-v1" or artifact.get("eligibility_version")!="origin-20200701-v1":
                raise StudyError("Frozen model protocol mismatch")
            self.artifacts[item["kind"]]=artifact
            self.hashes[item["kind"]]=item["sha256"]
        if set(self.artifacts)!={"baseline","aft"}:
            raise StudyError("Common-support comparison requires both frozen model families")
        if len({a["data_kind"] for a in self.artifacts.values()})!=1:
            raise StudyError("Cannot mix real and synthetic model artifacts")
        if any(a["data_kind"] not in ("real","synthetic") for a in self.artifacts.values()):
            raise StudyError("Unknown frozen model data kind")
        if self.artifacts["baseline"].get("model_version")!="kaplan-meier-v1" or self.artifacts["aft"].get("model_version")!="aft-v1":
            raise StudyError("Unsupported model version")
        if self.artifacts["aft"]["selection"]["status"]!="selected":
            raise StudyError("AFT diagnostic-only artifact cannot supply ordinary predictions")
        self.baseline,self.aft=self.artifacts["baseline"],self.artifacts["aft"]["fit"]
        cdf(self.aft,[{"old_rating":1000,"delta":0}],[1])  # validates frozen AFT parameters
        if type(self.baseline["delta_bin_width"]) is not int or self.baseline["delta_bin_width"]<=0:
            raise StudyError("Invalid frozen delta-bin width")
        prior=Path(directory)/"prior-samples.json"
        self.prior_samples=read_json(prior) if prior.exists() else {"samples":[]}
        self.prior_samples_sha256=file_sha(prior) if prior.exists() else None
        for sample in self.prior_samples["samples"]:
            values=[sample["selected_contest_ids"],*sample["partitions"].values()]
            if any(not isinstance(ids,list) or any(type(cid) is not int or cid<=0 for cid in ids) for ids in values):
                raise StudyError("Prior sample partitions must contain explicit contest ID lists")

    def common_sql(self):
        parts,params=[],[]
        for domain in (self.baseline["domain"],self.aft["domain"]):
            for field in ("old_rating","problem_rating","delta"):
                parts.append(f"{field} BETWEEN ? AND ?")
                params.extend(domain[field])
        width=self.baseline["delta_bin_width"]
        bins=sorted(int(k) for k in self.baseline["delta_bins"])
        if not bins:
            return "0",[]
        # Mathematical floor for negative deltas; SQLite integer division truncates.
        parts.append(f"(delta - ((delta % {width})+{width})%{width}) IN ({','.join('?' for _ in bins)})")
        params.extend(bins)
        return " AND ".join(parts),params

    def supported(self,old,problem):
        if any(type(v) is not int or v<=0 for v in (old,problem)):
            return False
        values={"old_rating":old,"problem_rating":problem,"delta":problem-old}
        for domain in (self.baseline["domain"],self.aft["domain"]):
            if any(not domain[k][0]<=v<=domain[k][1] for k,v in values.items()):
                return False
        bucket=str(((problem-old)//self.baseline["delta_bin_width"])*self.baseline["delta_bin_width"])
        return bucket in self.baseline["delta_bins"]

    def mixture(self,covariates,max_minutes):
        """Covariates (old, problem, multiplicity) of the very same observed subset."""
        if not covariates:
            return {kind:{"t50_minutes":None,"reason":"no_common_support"} for kind in ("aft","km_global","km_delta")}
        if any(not self.supported(old,problem) or type(n) is not int or n<1 for old,problem,n in covariates):
            raise StudyError("Mixture includes unsupported covariates; no implicit global fallback")
        weights=np.asarray([n for _,_,n in covariates],dtype=float)
        weights/=weights.sum()
        features=[{"old_rating":old,"delta":problem-old} for old,problem,_ in covariates]
        mu=locations(self.aft,features)
        aft_limit=min(max_minutes,self.aft["max_followup_minutes"])

        def aft_cdf(t):
            if t<=0:return 0.
            z=(np.log(t)-mu)/self.aft["scale"]
            values=ndtr(z) if self.aft["family"]=="lognormal" else -np.expm1(-np.exp(np.minimum(z,700)))
            return float(weights@values)

        if aft_limit<=0 or aft_cdf(aft_limit)<.5-1e-12:
            aft_median=None
        else:
            lo,hi=0.,aft_limit
            for _ in range(48):
                middle=(lo+hi)/2
                if aft_cdf(middle)>=.5:hi=middle
                else:lo=middle
            aft_median=hi
        result={"aft":{"t50_minutes":aft_median,"reason":None if aft_median is not None else "quantile_not_reached_in_common_followup",
                       "followup_minutes":aft_limit,"artifact_sha256":self.hashes["aft"]}}
        bucket_weights={}
        for (old,problem,n) in covariates:
            bucket=str(((problem-old)//self.baseline["delta_bin_width"])*self.baseline["delta_bin_width"])
            bucket_weights[bucket]=bucket_weights.get(bucket,0)+n
        denominator=sum(bucket_weights.values())
        for kind,curves in (("km_global",[(self.baseline["global"],1.)]),
                            ("km_delta",[(self.baseline["delta_bins"][b],n/denominator) for b,n in bucket_weights.items()])):
            limit=min([max_minutes,*[c["max_followup_seconds"]/60 for c,_ in curves]])
            times=np.asarray(sorted({r["seconds"]/60 for c,_ in curves for r in c["table"] if r["seconds"]/60<=limit}))
            values=sum(w*_step_values(c,times) for c,w in curves)
            crossing=np.flatnonzero(values>=.5-1e-12)
            median=float(times[crossing[0]]) if len(crossing) else None
            result[kind]={"t50_minutes":median,"reason":None if median is not None else "quantile_not_reached_in_common_followup",
                          "followup_minutes":limit,"artifact_sha256":self.hashes["baseline"]}
        return result

    def fixed(self,old,problems):
        if type(old) is not int or old<=0 or any(type(p) is not int or p<=0 for p in problems):
            raise StudyError("Fixed query ratings must be positive integers")
        result=[]
        for problem in problems:
            aft=predict_aft(self.artifacts["aft"],old,problem,[120],_sha=self.hashes["aft"])
            aft_value=None if aft["out_of_domain"] else aft["median_minutes"]
            predictions={"aft":{"t50_minutes":aft_value,"artifact_sha256":self.hashes["aft"],
                                "followup_minutes":self.aft["max_followup_minutes"],
                                "reason":"outside_model_domain" if aft["out_of_domain"] else None if aft_value is not None else "quantile_not_reached_in_model_followup"}}
            features={"old_rating":old,"problem_rating":problem,"delta":problem-old}
            outside=any(not self.baseline["domain"][k][0]<=v<=self.baseline["domain"][k][1] for k,v in features.items())
            bucket=str(((problem-old)//self.baseline["delta_bin_width"])*self.baseline["delta_bin_width"])
            for kind,curve in (("km_global",self.baseline["global"]),("km_delta",self.baseline["delta_bins"].get(bucket))):
                median=km_quantile(curve,.5) if curve is not None and not outside else None
                reason=("outside_model_domain" if outside else "delta_bin_unavailable; no global fallback" if curve is None
                        else None if median is not None else "quantile_not_reached_in_model_followup")
                predictions[kind]={"t50_minutes":median,"reason":reason,"artifact_sha256":self.hashes["baseline"],
                                   "followup_minutes":curve["max_followup_seconds"]/60 if curve else None}
            for kind,values in predictions.items():
                result.append({"mode":"fixed_covariates","old_rating":old,"problem_rating":problem,"method":kind,
                               "support_policy":"model-specific domain; no observed mixture/common population",**values})
            result.append({"mode":"fixed_covariates","old_rating":old,"problem_rating":problem,"method":"d012_operational",
                           "duration_minutes":expected_d012_minutes(old,problem),"t50_minutes":None,
                           "reason":"operational_duration; no CDF or calibrated median"})
        return result


def cell_support(db,where,params,at_risk):
    values=db.execute(f"""SELECT COUNT(*),COALESCE(SUM(event),0),COUNT(DISTINCT participant),
      COUNT(DISTINCT CAST(problem_contest_id AS TEXT)||':'||problem_index),COUNT(DISTINCT contest_id),
      COUNT(DISTINCT group_id),COALESCE(MAX(duration),0) FROM attempts WHERE {where}""",params).fetchone()
    result=dict(zip(("attempts","events","participants","problems","contests","groups","max_followup_seconds"),values))
    result["censored"]=result["attempts"]-result["events"]
    result["at_risk"]={str(t):db.execute(f"SELECT COUNT(*) FROM attempts WHERE {where} AND duration>=?",[*params,t*60]).fetchone()[0] for t in at_risk}
    return result


def support_reason(support,floors):
    if not support["attempts"]:return "no_observations"
    missing=[k for k in ("attempts","events","groups") if support[k]<floors[k]]
    return "insufficient_support:"+",".join(missing) if missing else None


def histogram(db,where,params,duration="duration"):
    if duration not in ("duration","last_submission","from_start"):
        raise StudyError("Unknown duration estimand")
    return list(db.execute(f"SELECT group_id,{duration},event,COUNT(*) FROM attempts WHERE {where} AND {duration}>0 GROUP BY group_id,{duration},event",params))


def analyze_scale(database,prepared,plan,models,output,*,uncertainty=True,sensitivities=True):
    plan=validate_plan(plan)
    if prepared["config_sha256"]!=plan["config_sha256"]:
        raise StudyError("Prepared population/config does not match the frozen plan")
    if {a["data_kind"] for a in models.artifacts.values()}!={prepared["data_kind"]}:
        raise StudyError("Cannot mix real observations and synthetic models (or vice versa)")
    output=Path(output); output.mkdir(parents=True,exist_ok=True)
    started=time.monotonic()
    db=sqlite3.connect(Path(database).resolve().as_uri()+"?mode=ro",uri=True)
    db.execute("PRAGMA cache_size=-65536"); db.execute("PRAGMA temp_store=FILE")
    db.execute("BEGIN")  # one consistent read snapshot across support/CDF queries
    try:
        snapshot_sha=db.execute("SELECT value FROM metadata WHERE name='snapshot_sha256'").fetchone()
        if not snapshot_sha or snapshot_sha[0]!=prepared["snapshot_sha256"]:
            raise StudyError("Database/preparation provenance mismatch")
        if prepared.get("database_sha256") and file_sha(database)!=prepared["database_sha256"]:
            raise StudyError("Analytic database checksum mismatch")
        all_groups=sorted(min(g) for g in prepared["groups"])
        count=len(all_groups)
        if not count:raise StudyError("No prepared groups")
        rng=np.random.default_rng(plan["bootstrap"]["seed"])
        draws=rng.multinomial(count,[1/count]*count,size=plan["bootstrap"]["replicates"])
        common,common_params=models.common_sql()
        rows=[]
        for old_lo,old_hi in zip(plan["old_rating_edges"],plan["old_rating_edges"][1:]):
            for problem_lo,problem_hi in zip(plan["problem_rating_edges"],plan["problem_rating_edges"][1:]):
                params=[old_lo,old_hi,problem_lo,problem_hi]
                cell="old_rating>=? AND old_rating<? AND problem_rating>=? AND problem_rating<?"
                where="quality='valid' AND "+cell
                support=cell_support(db,where,params,plan["at_risk_minutes"])
                hist=histogram(db,where,params)
                curve=weighted_km(hist)
                reason=support_reason(support,plan["support"])
                observed={"t50_minutes":curve["t50_minutes"] if reason is None else None,
                          "unfiltered_t50_minutes":curve["t50_minutes"],
                          "reason":reason or ("quantile_not_reached_in_followup" if curve["t50_minutes"] is None else None)}
                if uncertainty:
                    observed["uncertainty"]=grouped_uncertainty(hist,all_groups,draws)
                common_where=where+" AND "+common
                cp=params+common_params
                common_support=cell_support(db,common_where,cp,plan["at_risk_minutes"])
                common_hist=histogram(db,common_where,cp)
                common_curve=weighted_km(common_hist)
                common_reason=support_reason(common_support,plan["support"])
                covariates=list(db.execute(f"SELECT old_rating,problem_rating,COUNT(*) FROM attempts WHERE {common_where} GROUP BY old_rating,problem_rating",cp))
                predictions=models.mixture(covariates,common_curve["max_followup_seconds"]/60)
                common_observed={"t50_minutes":common_curve["t50_minutes"] if common_reason is None else None,
                                 "unfiltered_t50_minutes":common_curve["t50_minutes"],
                                 "reason":common_reason or ("quantile_not_reached_in_followup" if common_curve["t50_minutes"] is None else None)}
                if uncertainty:
                    common_observed["uncertainty"]=grouped_uncertainty(common_hist,all_groups,draws)
                for prediction in predictions.values():
                    prediction["unfiltered_t50_minutes"]=prediction["t50_minutes"]
                    if common_reason:
                        prediction.update(t50_minutes=None,reason=common_reason)
                quality=dict(db.execute(f"SELECT quality,COUNT(*) FROM attempts WHERE {cell} GROUP BY quality",params))
                row={"old_lower":old_lo,"old_upper":old_hi,"problem_lower":problem_lo,"problem_upper":problem_hi,
                     "problem_x":(problem_lo+problem_hi)/2,"mode":"observed_covariate_mixture",
                     "support":support,"observed":observed,"common_support":common_support,
                     "common_coverage":common_support["attempts"]/support["attempts"] if support["attempts"] else None,
                     "common_observed":common_observed,"predictions":predictions,
                     "observed_attempts":sum(quality.values()),"excluded":quality.get("excluded",0),
                     "weights":"one per eligible attempt; same rows for common KM and every mixture", "estimand":"sequential"}
                if sensitivities:
                    row["support_sensitivity"]=[{"floors":floors,"t50_minutes":curve["t50_minutes"] if support_reason(support,floors) is None else None,
                                                 "reason":support_reason(support,floors)} for floors in plan["support_sensitivity"]]
                    row["estimand_sensitivity"]={}
                    for estimand in ("last_submission","from_start"):
                        h=histogram(db,where,params,estimand)
                        c=weighted_km(h)
                        row["estimand_sensitivity"][estimand]={"attempts":c["n"],"events":c["events"],"t50_minutes":c["t50_minutes"],
                                                               "reason":None if c["t50_minutes"] is not None else "quantile_not_reached_in_followup",
                                                               "note":"different estimand; last-submission may omit zero/unknown intervals"}
                rows.append(row)
            print(f"analyzed user band=[{old_lo},{old_hi})",flush=True)
        total=prepared["observed_attempts"]
        valid=prepared["valid_events"]+prepared["valid_censored"]
        overlap={}
        selected=set(prepared["prepared_contests"])
        for name,artifact in models.artifacts.items():
            overlap[name]={field:sorted(selected&set(artifact.get(field,[]))) for field in ("training_contest_ids","validation_contest_ids")}
        # Group-level overlap captures selected contests linked to a training origin too.
        training=set(cid for a in models.artifacts.values() for cid in a.get("training_contest_ids",[])+a.get("validation_contest_ids",[]))
        overlap["known_origin_link_overlap"]=[link for link in prepared["origin_links"] if set(link["contest_ids"])&training]
        prior_overlap=[]
        for sample in models.prior_samples["samples"]:
            prior_ids=set(sample["selected_contest_ids"])
            prior_overlap.append({"source":sample["source"],"source_sha256":sample["source_sha256"],
                                  "snapshot_sha256":sample["snapshot_sha256"],
                                  "selected_overlap":sorted(selected&prior_ids),
                                  "partition_overlap":{name:sorted(selected&set(ids)) for name,ids in sample["partitions"].items()},
                                  "known_origin_link_overlap":[link for link in prepared["origin_links"] if set(link["contest_ids"])&prior_ids],
                                  "status":sample["status"]})
        overlap["prior_examined_samples"]=prior_overlap
        report={"schema_version":1,"version":"scale-t50-analysis-v1","data_kind":prepared["data_kind"],
                "plan_sha256":digest(plan),"snapshot_sha256":prepared["snapshot_sha256"],
                "prepared_sha256":digest(prepared),"selection_sha256":prepared["selection_sha256"],
                "plan":plan,"model_hashes":models.hashes,"units":"minutes","bin_edges":"[lower,upper)",
                "rows":rows,"total_observed":total,"total_valid":valid,
                "outside_grid_observed":total-sum(r["observed_attempts"] for r in rows),
                "outside_grid_valid":valid-sum(r["support"]["attempts"] for r in rows),
                "groups":prepared["groups"],"participants_repeated_across_groups":prepared["participants_repeated_across_groups"],
                "prepared_contest_ids":prepared["prepared_contests"],"eligible_contest_ids":prepared["eligible_contest_ids"],
                "overlap_with_examined_models":overlap,"new_independent_predictive_evaluation":"not performed; descriptive comparison only",
                "prior_sample_inventory_sha256":models.prior_samples_sha256,
                "model_uncertainty":"fixed model curves; observed CIs include group sampling only, not fitting uncertainty",
                "analysis_seconds":time.monotonic()-started,
                "limitations":["Sequential wall-clock time, not exclusive effort", "Informative censoring and selected prefixes", "No guarantee of 50% from equal Elo ratings", "Known aliases only; people repeat across groups", "KM global can coincide across bands; delta KM loses absolute levels"]}
        if sensitivities:
            report["grid_sensitivity"]=grid_sensitivity(db,plan)
        write_json(output/"analysis.json",report)
        export_table(report,output/"t50.csv")
        fixed=[r for old in plan["old_rating_edges"][:-1] if old>0 for r in models.fixed(old,list(range(800,3600,200)))]
        write_json(output/"fixed-queries.json",{"mode":"fixed scenario, not observed mixture","model_hashes":models.hashes,"rows":fixed})
        return report
    finally:
        db.close()


def grid_sensitivity(db,plan):
    result=[]
    for uw in plan["sensitivity_old_widths"]:
        for pw in plan["sensitivity_problem_widths"]:
            rows=[]
            for old in range(0,4000,uw):
                for problem in range(800,3600,pw):
                    where="quality='valid' AND old_rating>=? AND old_rating<? AND problem_rating>=? AND problem_rating<?"
                    params=[old,old+uw,problem,min(problem+pw,3600)]
                    counts=db.execute(f"SELECT COUNT(*),COALESCE(SUM(event),0),COUNT(DISTINCT group_id) FROM attempts WHERE {where}",params).fetchone()
                    support=dict(zip(("attempts","events","groups"),counts))
                    curve=weighted_km(histogram(db,where,params))
                    reason=support_reason(support,plan["support"])
                    rows.append({"old_lower":old,"old_upper":old+uw,"problem_lower":problem,"problem_upper":min(problem+pw,3600),
                                 **support,"t50_minutes":None if reason else curve["t50_minutes"],
                                 "reason":reason or ("quantile_not_reached_in_followup" if curve["t50_minutes"] is None else None)})
            result.append({"old_width":uw,"problem_width":pw,"rows":rows})
    return result


def export_table(report,path):
    columns=["old_lower","old_upper","problem_lower","problem_upper","mode","method","t50_minutes","reason",
             "attempts","events","censored","participants","problems","contests","groups","max_followup_seconds",
             "ci95_lower_minutes","ci95_upper_minutes","missing_replicates","common_coverage","snapshot_sha256","plan_sha256","selection_sha256","artifact_sha256",
             "analysis_version","model_version","units","population","weights"]
    with Path(path).open("w",encoding="utf-8",newline="") as stream:
        writer=csv.DictWriter(stream,fieldnames=columns); writer.writeheader()
        for cell in report["rows"]:
            for method,value in [("observed",cell["observed"]),("common_observed",cell["common_observed"]),*cell["predictions"].items()]:
                common=method!="observed"
                support=cell["common_support"] if common else cell["support"]
                ci=value.get("uncertainty",{}).get("ci95_minutes")
                row={k:cell[k] for k in ("old_lower","old_upper","problem_lower","problem_upper","mode","common_coverage")}
                row.update({k:support[k] for k in ("attempts","events","censored","participants","problems","contests","groups","max_followup_seconds")})
                row.update(method=method,t50_minutes=value["t50_minutes"],reason=value["reason"],
                           ci95_lower_minutes=ci[0] if ci else None,ci95_upper_minutes=ci[1] if ci else None,
                           missing_replicates=value.get("uncertainty",{}).get("missing_replicates"),
                           snapshot_sha256=report["snapshot_sha256"],plan_sha256=report["plan_sha256"],
                           selection_sha256=report.get("selection_sha256"),
                           artifact_sha256=value.get("artifact_sha256") or report.get("model_hashes",{}).get("aft" if method=="aft" else "baseline" if method in ("km_global","km_delta") else "observed"),
                           analysis_version=report["version"],model_version="aft-v1" if method=="aft" else "kaplan-meier-v1",
                           units="minutes",population="common model support" if common else "all eligible attempts in cell",
                           weights="one per eligible attempt; common rows shared by observed and model mixtures")
                writer.writerow(row)


def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("analysis_root",type=Path)
    parser.add_argument("--plan",type=Path,required=True)
    parser.add_argument("--models",type=Path,required=True)
    parser.add_argument("--output",type=Path,required=True)
    args=parser.parse_args(argv)
    plan=read_json(args.plan)
    models=FrozenModels(args.models,plan["model_inventory"])
    from .cli import work_lock
    with work_lock(args.analysis_root):
        report=analyze_scale(args.analysis_root/"attempts.sqlite",read_json(args.analysis_root/"manifest.json"),plan,models,args.output)
    print({"cells":len(report["rows"]),"valid":report["total_valid"],"seconds":report["analysis_seconds"]})


if __name__=="__main__":main()
