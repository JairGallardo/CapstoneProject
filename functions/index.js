// functions/index.js
// Despliega con: firebase deploy --only functions

const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

/**
 * Cloud Function callable desde Android via FirebaseFunctions.
 * Reemplaza la llamada directa a fcm.googleapis.com/fcm/send
 * que fue desactivada en junio 2024.
 */
exports.enviarNotificacion = functions.https.onCall(async (data, context) => {
    const { token, titulo, cuerpo, chatId, esTrabajador, nombreSocio } = data;

    if (!token || !chatId) {
        throw new functions.https.HttpsError(
            "invalid-argument",
            "Faltan campos obligatorios: token o chatId"
        );
    }

    const mensaje = {
        token: token,
        notification: {
            title: titulo || "Domingo",
            body: cuerpo || ""
        },
        data: {
            chatId: chatId,
            esTrabajador: String(esTrabajador ?? false),
            nombreSocio: nombreSocio ?? "Socio"
        },
        android: {
            priority: "high",
            notification: {
                channelId: "domingo_chat_channel",
                sound: "default"
            }
        },
        apns: {
            payload: {
                aps: { sound: "default", badge: 1 }
            }
        }
    };

    try {
        const response = await admin.messaging().send(mensaje);
        console.log("Notificación enviada OK:", response);
        return { success: true, messageId: response };
    } catch (error) {
        console.error("Error enviando notificación:", error);
        throw new functions.https.HttpsError("internal", error.message);
    }
});