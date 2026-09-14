package com.company.taskmanagementplatform.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.web.header.HeaderWriter;

/**
 * The four headers the framework does not write, on every response.
 *
 * <p>One writer rather than four registrations, and one {@code Content-Security-Policy} rather than
 * two. That second point is the reason this class exists at all: when two writers each set a policy
 * the browser enforces the <em>intersection</em> of them, so registering a strict policy for the API
 * and a looser one for the documentation console would not give the console the looser policy, it
 * would give it neither. Choosing between them here means exactly one policy header leaves the
 * application and it is the right one.
 *
 * <p><strong>The API policy is {@code default-src 'none'}.</strong> Every response from this
 * application except the documentation console is JSON, or a file served as an attachment. None of it
 * is a document a browser renders, so nothing legitimate needs a source permitted. The three
 * directives beside it close the things {@code default-src} does not cover: {@code frame-ancestors}
 * refuses framing, which is the same answer {@code X-Frame-Options} gives to older browsers; {@code
 * base-uri} stops an injected {@code <base>} from re-pointing every relative URL on a page; {@code
 * form-action} stops an injected form from posting somewhere else.
 *
 * <p><strong>The console policy is looser because Swagger UI cannot run under the strict one.</strong>
 * It needs its own scripts and styles, and it configures itself through an inline script, so {@code
 * 'unsafe-inline'} is required for it to work at all. That is an acceptable trade for a page that
 * renders a document this application generated, and the exposure is bounded twice over: the console
 * is switched off entirely under the prod profile, and the policy still refuses framing and still
 * confines every source to this origin. {@code /v3/api-docs} is deliberately <em>not</em> included —
 * it is JSON and takes the strict policy; the console reaches it over {@code connect-src 'self'}.
 *
 * <p><strong>{@code Cross-Origin-Resource-Policy: same-origin} is safe for this frontend, and that
 * had to be checked rather than assumed.</strong> The header refuses cross-origin <em>no-cors</em>
 * loads: an {@code <img>}, a {@code <script>}, an {@code <iframe>} pointed at this API from another
 * origin. It does not touch a CORS request, and the single-page application fetches attachment
 * content through the shared authorized client and turns it into a blob rather than embedding it, so
 * nothing it does is affected. If a client ever needs to embed a download directly — an image
 * preview with a bare {@code <img src>} — this is the header that will refuse it, and {@code
 * same-site} is the value that would allow a sibling origin to.
 *
 * <p>{@code Cross-Origin-Opener-Policy} costs nothing here. An API response is not a browsing
 * context, so the header has nothing to sever; it is set so that a browser which does treat one of
 * these responses as a document cannot leave it sharing a context with its opener.
 *
 * <p>{@code Permissions-Policy} denies every powerful feature outright. An API has no use for a
 * camera, and an empty allowlist on each is the strongest thing the header can say.
 */
class ResponseSecurityHeaders implements HeaderWriter {

    /**
     * For JSON, and for a download. Nothing may be loaded, framed, based or posted.
     */
    static final String API_CONTENT_SECURITY_POLICY =
            "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";

    /**
     * For the Swagger console only. Everything is confined to this origin; inline script and style
     * are what the console needs to start, and framing is still refused.
     */
    static final String DOCS_CONTENT_SECURITY_POLICY = "default-src 'self'; "
            + "script-src 'self' 'unsafe-inline'; "
            + "style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data:; "
            + "font-src 'self' data:; "
            + "connect-src 'self'; "
            + "worker-src 'self' blob:; "
            + "frame-ancestors 'none'; "
            + "base-uri 'self'; "
            + "form-action 'self'";

    /** Every feature this application could be asked for, denied. */
    static final String PERMISSIONS_POLICY = "accelerometer=(), ambient-light-sensor=(), autoplay=(), "
            + "camera=(), display-capture=(), encrypted-media=(), fullscreen=(), geolocation=(), "
            + "gyroscope=(), magnetometer=(), microphone=(), midi=(), payment=(), "
            + "picture-in-picture=(), publickey-credentials-get=(), screen-wake-lock=(), "
            + "usb=(), xr-spatial-tracking=()";

    static final String CONTENT_SECURITY_POLICY_HEADER = "Content-Security-Policy";
    static final String PERMISSIONS_POLICY_HEADER = "Permissions-Policy";
    static final String OPENER_POLICY_HEADER = "Cross-Origin-Opener-Policy";
    static final String RESOURCE_POLICY_HEADER = "Cross-Origin-Resource-Policy";

    private static final String SWAGGER_PAGE = "/swagger-ui.html";
    private static final String SWAGGER_ASSETS = "/swagger-ui/";

    @Override
    public void writeHeaders(HttpServletRequest request, HttpServletResponse response) {
        // setHeader, not addHeader: a second policy header would be intersected
        // with the first, which is the trap this class exists to avoid.
        response.setHeader(CONTENT_SECURITY_POLICY_HEADER, policyFor(request));
        response.setHeader(PERMISSIONS_POLICY_HEADER, PERMISSIONS_POLICY);
        response.setHeader(OPENER_POLICY_HEADER, "same-origin");
        response.setHeader(RESOURCE_POLICY_HEADER, "same-origin");
    }

    /**
     * The console's own page and its assets get the looser policy. Everything else, including the
     * generated document the console reads, gets the strict one.
     */
    private static String policyFor(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return API_CONTENT_SECURITY_POLICY;
        }

        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }

        boolean isConsole = path.equals(SWAGGER_PAGE) || path.startsWith(SWAGGER_ASSETS);
        return isConsole ? DOCS_CONTENT_SECURITY_POLICY : API_CONTENT_SECURITY_POLICY;
    }
}
