package com.BreadIQ.myapp.core

import android.net.Uri

/**
 * Ported from the iOS app's `Navigation/AppRouter.swift`'s
 * `urlDeepLinkDestination(for:)` / `passwordRecoveryDestination(fromFragment:)`
 * / `fragmentParameters(_:)`, operating on [android.net.Uri] instead of
 * `URL`/`URLComponents`.
 */
sealed class DeepLinkDestination {
    /**
     * `breadiq-mobile://import?token=xyz` — host `import`, requires a
     * non-empty `token` query param. Captured by `MainActivity.kt` into
     * `pendingImportToken`, not yet consumed by anything —
     * `PendingImportsListScreen` (a separate, explicitly out-of-scope
     * follow-up session) is the intended consumer.
     */
    data class ImportToken(val token: String) : DeepLinkDestination()

    /**
     * `breadiq-mobile://reset-password#access_token=...&refresh_token=...&type=recovery`
     * and `https://breadiq.io/reset-password#...` /
     * `https://www.breadiq.io/reset-password#...` both funnel here — the
     * first real consumer of this infrastructure,
     * [com.BreadIQ.myapp.screens.SetNewPasswordScreen].
     */
    data class PasswordRecovery(val accessToken: String, val refreshToken: String) : DeepLinkDestination()

    data object None : DeepLinkDestination()
}

/**
 * Port of `urlDeepLinkDestination(for:)`. `token`/fragment params are
 * truthy-checked the way the source's JS/Swift both do (non-nil AND
 * non-empty), matching `AppRouter.urlDeepLinkDestination`'s own doc
 * comment on why only the empty-string case needs an explicit check
 * once a lookup already returns `null` for a missing param.
 *
 * **A real `https` URL routes on its PATH (the host is the domain
 * itself, `breadiq.io`), unlike the custom scheme's own host-based
 * routing below (where `reset-password` IS the host, since
 * `breadiq-mobile://` has no domain of its own)** — genuinely different
 * shapes, not just a cosmetic difference, so this needs its own branch
 * rather than falling through the same host `when`. Only `reset-password`
 * is wired for `https` — the import flow has no App Links counterpart
 * either, matching iOS.
 *
 * **This function alone does not make `https://breadiq.io/reset-password`
 * links actually open this app.** App Links verification
 * (`android:autoVerify="true"`, see `AndroidManifest.xml`) requires
 * Google to successfully fetch and validate
 * `https://breadiq.io/.well-known/assetlinks.json`, listing this app's
 * `applicationId` and signing certificate's SHA-256 fingerprint — hosted
 * on `breadiq.io`'s web server, outside this repo. Until that file
 * exists and verifies, a tapped `https://breadiq.io/reset-password` link
 * opens in Chrome (or an app-picker), not this app — safe, inert,
 * dead-until-configured, not a behavior change. The custom-scheme path
 * (`breadiq-mobile://reset-password#...`) works immediately, independent
 * of that external file.
 */
fun deepLinkDestination(uri: Uri): DeepLinkDestination {
    if (uri.scheme?.lowercase() == "https") {
        val host = uri.host?.lowercase()
        if (host != "breadiq.io" && host != "www.breadiq.io") return DeepLinkDestination.None
        if (uri.path?.lowercase() != "/reset-password") return DeepLinkDestination.None
        return passwordRecoveryDestination(uri)
    }
    return when (uri.host?.lowercase()) {
        "import" -> uri.getQueryParameter("token")?.takeIf { it.isNotEmpty() }
            ?.let { DeepLinkDestination.ImportToken(it) } ?: DeepLinkDestination.None
        "reset-password" -> passwordRecoveryDestination(uri)
        else -> DeepLinkDestination.None
    }
}

/**
 * Shared by both the custom-scheme and App Links routes above — same
 * fragment shape either way, since both are ultimately fed by the same
 * GoTrue `/verify?type=recovery` redirect.
 *
 * **A real, non-obvious wrinkle, live-confirmed on the iOS port before
 * writing this**: GoTrue's `/verify?type=recovery` redirect encodes the
 * session as URL FRAGMENT parameters (`#access_token=...`), not query
 * parameters — [Uri.getQueryParameter] would never match. Confirmed by
 * actually calling `/recover` against the live project and following the
 * real emailed link.
 */
private fun passwordRecoveryDestination(uri: Uri): DeepLinkDestination {
    val fragment = uri.encodedFragment ?: return DeepLinkDestination.None
    val params = fragmentParameters(fragment)
    if (params["type"] != "recovery") return DeepLinkDestination.None
    val accessToken = params["access_token"]?.takeIf { it.isNotEmpty() } ?: return DeepLinkDestination.None
    val refreshToken = params["refresh_token"]?.takeIf { it.isNotEmpty() } ?: return DeepLinkDestination.None
    return DeepLinkDestination.PasswordRecovery(accessToken, refreshToken)
}

/**
 * Parses a URL fragment shaped like a query string
 * (`access_token=...&refresh_token=...&type=recovery`) into its
 * key/value pairs.
 *
 * **Do not use [Uri.getFragment] for the input here — a real, non-obvious
 * wrinkle.** [Uri.getFragment] returns the ALREADY percent-decoded
 * fragment; iOS's own `fragmentParameters(_:)` splits the RAW fragment
 * on `&`/`=` first, THEN percent-decodes each value individually — a
 * meaningfully different order when a token value could itself contain
 * an encoded `&` or `=` (JWTs generally don't, but `refresh_token` isn't
 * guaranteed to). This function takes the caller's already-encoded
 * fragment ([Uri.getEncodedFragment]) and decodes each value itself,
 * matching that order exactly.
 */
private fun fragmentParameters(encodedFragment: String): Map<String, String> =
    encodedFragment.split("&").mapNotNull { pair ->
        val parts = pair.split("=", limit = 2)
        if (parts.size != 2) null else parts[0] to Uri.decode(parts[1])
    }.toMap()
