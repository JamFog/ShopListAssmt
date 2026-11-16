package com.mmgpt.shoplistgpt.spreadsheet

/** File types the app reads and writes, and how an incoming file's type is decided. */
object SpreadsheetFormat {
    const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    /** MIME types offered by the import file picker: CSV or Excel, falling back to anything. */
    val IMPORT_MIME_TYPES = arrayOf(
        "text/csv",
        "application/vnd.ms-excel",
        "text/comma-separated-values",
        XLSX_MIME,
        "*/*"
    )

    /**
     * True if the file should be parsed as XLSX: either the system reports the XLSX MIME
     * type or the name ends in ".xlsx". Everything else is treated as CSV.
     */
    fun isXlsx(mimeType: String?, name: String): Boolean =
        mimeType == XLSX_MIME || name.endsWith(".xlsx", ignoreCase = true)
}
