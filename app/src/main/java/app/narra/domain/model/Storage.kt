package app.narra.domain.model

/** Espacio que ocupa Narra en el teléfono, por tipo de archivo. */
data class StorageUsage(
    /** Audio generado de todos los libros. */
    val audioBytes: Long,
    /** PDF importados. */
    val sourceBytes: Long,
    /** Texto analizado, OCR y portadas. */
    val workingBytes: Long,
    /** Temporales de la creación y la exportación: se pueden borrar sin perder nada. */
    val tempBytes: Long,
    /** Libre en el almacenamiento del teléfono. */
    val availableBytes: Long,
) {
    val totalBytes: Long get() = audioBytes + sourceBytes + workingBytes + tempBytes
}
