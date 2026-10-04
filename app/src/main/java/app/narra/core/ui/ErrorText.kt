package app.narra.core.ui

import app.narra.domain.model.ErrorKind

/** Qué puede hacer el usuario ante un error. La pantalla decide cómo ejecutarlo. */
enum class RecoveryAction { RETRY, FREE_SPACE, CHOOSE_ANOTHER_FILE, RUN_OCR, INSTALL_VOICE, CHOOSE_VOICE, DELETE }

/** Error explicado en tres partes: qué pasó, por qué importa y qué hacer. */
data class ErrorText(
    val title: String,
    val explanation: String,
    val action: RecoveryAction?,
    val actionLabel: String?,
)

fun ErrorKind.toErrorText(): ErrorText = when (this) {
    ErrorKind.STORAGE_FULL -> ErrorText(
        title = "No hay suficiente espacio",
        explanation = "El teléfono se quedó sin espacio para seguir generando el audiolibro. Lo ya generado se conserva.",
        action = RecoveryAction.FREE_SPACE,
        actionLabel = "Liberar espacio",
    )
    ErrorKind.FILE_MISSING -> ErrorText(
        title = "Falta el archivo original",
        explanation = "No encontramos la copia del PDF de este libro. Vuelve a importarlo para continuar.",
        action = RecoveryAction.CHOOSE_ANOTHER_FILE,
        actionLabel = "Importar de nuevo",
    )
    ErrorKind.PDF_ENCRYPTED -> ErrorText(
        title = "El PDF está protegido",
        explanation = "Tiene contraseña o restricciones que impiden leer su texto. Quita la protección con la app que lo creó e impórtalo de nuevo.",
        action = RecoveryAction.CHOOSE_ANOTHER_FILE,
        actionLabel = "Elegir otro archivo",
    )
    ErrorKind.PDF_CORRUPT -> ErrorText(
        title = "No pudimos abrir este PDF",
        explanation = "El archivo parece dañado o incompleto. Prueba a descargarlo otra vez.",
        action = RecoveryAction.CHOOSE_ANOTHER_FILE,
        actionLabel = "Elegir otro archivo",
    )
    ErrorKind.PDF_EMPTY -> ErrorText(
        title = "El PDF está vacío",
        explanation = "No tiene páginas que leer.",
        action = RecoveryAction.CHOOSE_ANOTHER_FILE,
        actionLabel = "Elegir otro archivo",
    )
    ErrorKind.NO_TEXT -> ErrorText(
        title = "No encontramos texto",
        explanation = "Las páginas parecen imágenes escaneadas. Podemos reconocer el texto en tu teléfono antes de crear el audiolibro.",
        action = RecoveryAction.RUN_OCR,
        actionLabel = "Reconocer texto",
    )
    ErrorKind.OCR_FAILED -> ErrorText(
        title = "No se pudo reconocer el texto",
        explanation = "El reconocimiento de texto falló en algunas páginas. Puedes reintentarlo.",
        action = RecoveryAction.RETRY,
        actionLabel = "Reintentar",
    )
    ErrorKind.TTS_UNAVAILABLE -> ErrorText(
        title = "No hay un motor de voz",
        explanation = "Para crear audio, Android necesita un motor de texto a voz, como \"Servicios de voz de Google\".",
        action = RecoveryAction.INSTALL_VOICE,
        actionLabel = "Configurar voz",
    )
    ErrorKind.VOICE_UNAVAILABLE -> ErrorText(
        title = "La voz elegida no está disponible",
        explanation = "No está descargada en el teléfono o se desinstaló. Elige otra voz o descárgala en los ajustes de voz.",
        action = RecoveryAction.CHOOSE_VOICE,
        actionLabel = "Elegir voz",
    )
    ErrorKind.SYNTHESIS_FAILED -> ErrorText(
        title = "La voz falló en un fragmento",
        explanation = "El motor de voz no pudo leer parte del texto. Reintentar suele resolverlo.",
        action = RecoveryAction.RETRY,
        actionLabel = "Reintentar",
    )
    ErrorKind.ENCODING_FAILED -> ErrorText(
        title = "No se pudo guardar el audio",
        explanation = "Hubo un problema al comprimir el audio de un fragmento.",
        action = RecoveryAction.RETRY,
        actionLabel = "Reintentar",
    )
    ErrorKind.NETWORK -> ErrorText(
        title = "Sin conexión",
        explanation = "La voz elegida necesita internet. Continuaremos cuando vuelvas a estar conectado.",
        action = RecoveryAction.RETRY,
        actionLabel = "Reintentar",
    )
    ErrorKind.UNKNOWN -> ErrorText(
        title = "Algo salió mal",
        explanation = "Ocurrió un error inesperado. Lo ya procesado se conserva.",
        action = RecoveryAction.RETRY,
        actionLabel = "Reintentar",
    )
}
