package com.secondmonday.hodith.viewmodel

/**
 * The PDF footer's one line linking back to the app (feature/pdf-export) - blank until HODITH has
 * a public page to point to, so filling this in later is a one-line change. Printed as plain
 * text: `android.graphics.pdf.PdfDocument` has no clickable-annotation API, though most PDF
 * viewers still auto-linkify a bare URL found in plain text.
 */
internal const val HODITH_APP_LINK_URL = ""

internal fun pdfAppLinkText(): String? = HODITH_APP_LINK_URL.takeIf(String::isNotBlank)
