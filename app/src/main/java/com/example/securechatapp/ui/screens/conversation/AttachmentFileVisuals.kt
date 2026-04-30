package com.example.securechatapp.ui.screens.conversation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.securechatapp.R
import java.util.Locale

private enum class AttachmentFileKind {
    TEXT,
    CODE,
    DOCUMENT,
    PDF,
    SHEET,
    ARCHIVE,
    PACKAGE,
    EXECUTABLE,
    DESIGN,
    DATABASE,
    TORRENT,
    AUDIO,
    VIDEO,
    GENERIC,
}

@Composable
fun AttachmentFileIcon(
    fileName: String,
    mimeType: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val kind = resolveAttachmentFileKind(fileName = fileName, mimeType = mimeType)
    Icon(
        painter = painterResource(id = kind.drawableRes),
        contentDescription = contentDescription,
        modifier = modifier.size(28.dp),
        tint = kind.tintColor(),
    )
}

private fun resolveAttachmentFileKind(
    fileName: String,
    mimeType: String?,
): AttachmentFileKind {
    val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
        .lowercase(Locale.ROOT)
    val normalizedMime = mimeType?.lowercase(Locale.ROOT).orEmpty()

    return when {
        normalizedMime.startsWith("audio/") -> AttachmentFileKind.AUDIO
        normalizedMime.startsWith("video/") -> AttachmentFileKind.VIDEO
        normalizedMime in setOf(
            "application/vnd.android.package-archive",
            "application/x-msdownload",
            "application/x-dosexec",
            "application/x-msi",
            "application/x-apple-diskimage",
            "application/x-debian-package",
            "application/vnd.microsoft.portable-executable",
        ) && extension in setOf("exe", "msi", "bin", "run", "appimage", "deb", "rpm", "dmg", "pkg", "bat", "cmd", "ps1") ->
            AttachmentFileKind.EXECUTABLE
        normalizedMime in setOf(
            "application/vnd.android.package-archive",
            "application/java-archive",
            "application/vnd.apple.installer+xml",
        ) || extension in setOf("apk", "xapk", "aab", "ipa", "jar") -> AttachmentFileKind.PACKAGE
        normalizedMime in setOf(
            "application/x-msdownload",
            "application/x-dosexec",
            "application/x-msi",
            "application/x-apple-diskimage",
            "application/x-debian-package",
            "application/vnd.microsoft.portable-executable",
        ) || extension in setOf("exe", "msi", "bin", "run", "appimage", "deb", "rpm", "dmg", "pkg", "bat", "cmd", "ps1") ->
            AttachmentFileKind.EXECUTABLE
        normalizedMime in setOf(
            "image/vnd.adobe.photoshop",
            "application/octet-stream",
        ) && extension in setOf("psd", "fig", "sketch", "xd", "ai") -> AttachmentFileKind.DESIGN
        extension in setOf("psd", "fig", "sketch", "xd", "ai") -> AttachmentFileKind.DESIGN
        normalizedMime in setOf(
            "application/vnd.sqlite3",
            "application/x-sqlite3",
            "application/x-msaccess",
        ) || extension in setOf("db", "sqlite", "sqlite3", "db3", "sqlitedb", "mdb", "accdb") ->
            AttachmentFileKind.DATABASE
        normalizedMime == "application/x-bittorrent" || extension == "torrent" -> AttachmentFileKind.TORRENT
        normalizedMime == "application/pdf" || extension == "pdf" -> AttachmentFileKind.PDF
        normalizedMime in setOf(
            "application/zip",
            "application/x-zip-compressed",
            "application/x-rar-compressed",
            "application/x-7z-compressed",
            "application/gzip",
            "application/x-tar",
        ) || extension in setOf("zip", "rar", "7z", "gz", "tar") -> AttachmentFileKind.ARCHIVE
        normalizedMime in setOf(
            "text/csv",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        ) || extension in setOf("csv", "xls", "xlsx") -> AttachmentFileKind.SHEET
        normalizedMime in setOf(
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.oasis.opendocument.text",
        ) || extension in setOf("doc", "docx", "odt", "rtf") -> AttachmentFileKind.DOCUMENT
        normalizedMime.startsWith("text/") && extension in setOf("txt", "md", "log", "ini", "cfg") -> AttachmentFileKind.TEXT
        normalizedMime.startsWith("text/") && extension in setOf(
            "py", "kt", "kts", "kv", "java", "js", "ts", "tsx", "jsx", "c", "cpp", "h",
            "hpp", "cs", "go", "rs", "php", "rb", "swift", "xml", "json", "yaml", "yml",
            "toml", "html", "css", "scss", "sql", "sh"
        ) -> AttachmentFileKind.CODE
        extension in setOf("txt", "md", "log", "ini", "cfg") -> AttachmentFileKind.TEXT
        extension in setOf(
            "py", "kt", "kts", "kv", "java", "js", "ts", "tsx", "jsx", "c", "cpp", "h",
            "hpp", "cs", "go", "rs", "php", "rb", "swift", "xml", "json", "yaml", "yml",
            "toml", "html", "css", "scss", "sql", "sh"
        ) -> AttachmentFileKind.CODE
        else -> AttachmentFileKind.GENERIC
    }
}

private val AttachmentFileKind.drawableRes: Int
    @DrawableRes
    get() = when (this) {
        AttachmentFileKind.TEXT -> R.drawable.ic_attachment_file_text
        AttachmentFileKind.CODE -> R.drawable.ic_attachment_file_code
        AttachmentFileKind.DOCUMENT -> R.drawable.ic_attachment_file_doc
        AttachmentFileKind.PDF -> R.drawable.ic_attachment_file_pdf
        AttachmentFileKind.SHEET -> R.drawable.ic_attachment_file_sheet
        AttachmentFileKind.ARCHIVE -> R.drawable.ic_attachment_file_archive
        AttachmentFileKind.PACKAGE -> R.drawable.ic_attachment_file_package
        AttachmentFileKind.EXECUTABLE -> R.drawable.ic_attachment_file_executable
        AttachmentFileKind.DESIGN -> R.drawable.ic_attachment_file_design
        AttachmentFileKind.DATABASE -> R.drawable.ic_attachment_file_database
        AttachmentFileKind.TORRENT -> R.drawable.ic_attachment_file_torrent
        AttachmentFileKind.AUDIO -> R.drawable.ic_attachment_file_audio
        AttachmentFileKind.VIDEO -> R.drawable.ic_attachment_file_video
        AttachmentFileKind.GENERIC -> R.drawable.ic_attachment_file_generic
    }

@Composable
private fun AttachmentFileKind.tintColor(): Color {
    return when (this) {
        AttachmentFileKind.TEXT -> Color(0xFF4A6FA5)
        AttachmentFileKind.CODE -> Color(0xFF2F855A)
        AttachmentFileKind.DOCUMENT -> Color(0xFF2B6CB0)
        AttachmentFileKind.PDF -> Color(0xFFC53030)
        AttachmentFileKind.SHEET -> Color(0xFF2F855A)
        AttachmentFileKind.ARCHIVE -> Color(0xFF8C5E34)
        AttachmentFileKind.PACKAGE -> Color(0xFF805AD5)
        AttachmentFileKind.EXECUTABLE -> Color(0xFFDD6B20)
        AttachmentFileKind.DESIGN -> Color(0xFFD53F8C)
        AttachmentFileKind.DATABASE -> Color(0xFF3182CE)
        AttachmentFileKind.TORRENT -> Color(0xFF2C7A7B)
        AttachmentFileKind.AUDIO -> Color(0xFF7B61C8)
        AttachmentFileKind.VIDEO -> Color(0xFFE67E22)
        AttachmentFileKind.GENERIC -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}
