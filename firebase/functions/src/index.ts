import {initializeApp} from "firebase-admin/app";
import {FieldValue, getFirestore} from "firebase-admin/firestore";
import {HttpsError, onCall} from "firebase-functions/v2/https";

initializeApp();

/** Only the verified token chooses the document path. Admin SDK bypasses Rules. */
export const bootstrapAccount = onCall(
  {region: "us-central1", maxInstances: 5, timeoutSeconds: 20},
  async (request): Promise<{created: boolean}> => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Entre com Google.");
    if (request.auth.token.firebase?.sign_in_provider !== "google.com") {
      throw new HttpsError("permission-denied", "Somente o login Google e permitido.");
    }
    if (request.data === null || typeof request.data !== "object" ||
        Array.isArray(request.data) || Object.keys(request.data).length !== 0) {
      throw new HttpsError("invalid-argument", "Este comando nao recebe campos.");
    }

    const db = getFirestore();
    const account = db.collection("users").doc(request.auth.uid);
    const created = await db.runTransaction(async (transaction) => {
      const current = await transaction.get(account);
      if (current.exists) return false;
      transaction.create(account, {
        schemaVersion: 1,
        createdAt: FieldValue.serverTimestamp(),
      });
      return true;
    });
    return {created};
  },
);
