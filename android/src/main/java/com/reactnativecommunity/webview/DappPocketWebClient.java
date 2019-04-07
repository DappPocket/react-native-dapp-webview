package com.reactnativecommunity.webview;

import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class DappPocketWebClient extends WebViewClient {
    private final String TAG = "DappPocketWebClient";
    private static final String DEFAULT_CHARSET = "utf-8";
    private static final String DEFAULT_MIME_TYPE = "text/html";
    private OkHttpClient httpClient;
    private String injectJsCode;

    public DappPocketWebClient() {
        httpClient = new OkHttpClient.Builder().cookieJar(new WebViewCookieJar()).build();
    }

    public void setInjectJsCode(String injectJsCode) {
        Log.d(TAG, injectJsCode);
        this.injectJsCode = injectJsCode;
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        Log.d(TAG, "url: " + request.getUrl() + ", isForMainFrame: " + request.isForMainFrame());
        if (!request.isForMainFrame()) {
            return null;
        }

        String url = request.getUrl().toString();
        Map<String, String> headers = request.getRequestHeaders();
        Request.Builder requestBuilder = new Request.Builder().get().url(url);
        Set<String> keys = headers.keySet();
        for (String key : keys) {
            requestBuilder.addHeader(key, headers.get(key));
        }
        Request mockRequest = requestBuilder.build();
        Response response = null;
        String body = null;
        try {
            response = httpClient.newCall(mockRequest).execute();
        } catch (Exception e) {
            Log.d(TAG, "" + e);
            return null;
        }
        try {
            if (response.isSuccessful()) {
                body = response.body().string();
            }
        } catch (IOException ex) {
            Log.d("READ_BODY_ERROR", "Ex", ex);
        }

        if (body == null) {
            return null;
        }

        String contentType = getContentTypeHeader(response);
        String charset = getCharset(contentType);
        String mime = getMimeType(contentType);
        Response prior = response.priorResponse();
        boolean isRedirect = prior != null && prior.isRedirect();

        if (response == null || isRedirect) {
            Matcher curUrlRegResult = Pattern.compile("dapppocket.io").matcher(url);
            if (curUrlRegResult.find()){
                Log.d(TAG, "callback from fb!!!!!");
            } else {
                return null;
            }
        }

        String result = handleContentSecurityPolicyTag(body);
        result = injectJS(result, injectJsCode);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(result.getBytes());
        WebResourceResponse webResourceResponse = new WebResourceResponse(
                mime, charset, inputStream);

        return webResourceResponse;
    }

    private String getMimeType(String contentType) {
        Matcher regexResult = Pattern.compile("^.*(?=;)").matcher(contentType);
        if (regexResult.find()) {
            return regexResult.group();
        }
        return DEFAULT_MIME_TYPE;
    }

    private String getCharset(String contentType) {
        Matcher regexResult = Pattern.compile("charset=([a-zA-Z0-9-]+)").matcher(contentType);
        if (regexResult.find()) {
            if (regexResult.groupCount() >= 2) {
                return regexResult.group(1);
            }
        }
        return DEFAULT_CHARSET;
    }

    @Nullable
    private String getContentTypeHeader(Response response) {
        Headers headers = response.headers();
        String contentType;
        if (TextUtils.isEmpty(headers.get("Content-Type"))) {
            if (TextUtils.isEmpty(headers.get("content-Type"))) {
                contentType = "text/data; charset=utf-8";
            } else {
                contentType = headers.get("content-Type");
            }
        } else {
            contentType = headers.get("Content-Type");
        }
        if (contentType != null) {
            contentType = contentType.trim();
        }
        return contentType;
    }

    private String injectJS(String html, String js) {
        String script = "<script>" + js + "</script>\n";
        if (TextUtils.isEmpty(html)) {
            return html;
        }
        int position = getInjectionPosition(html);
        if (position > 0) {
            String beforeTag = html.substring(0, position);
            String afterTag = html.substring(position);

            String result = beforeTag + script + afterTag;
            return result;
        }
        return html;
    }

    private int getInjectionPosition(String body) {
        body = body.toLowerCase();
        int ieDetectTagIndex = body.indexOf("<!--[if");
        int commentTagIndex = body.indexOf("<!--");
        int headerTagIndex = body.indexOf("<head");

        int index;
        if (ieDetectTagIndex < 0) {
            index = headerTagIndex;
        } else {
            index = Math.min(headerTagIndex, ieDetectTagIndex);
            index = Math.min(index, commentTagIndex);
        }
        if (index < 0) {
            index = body.indexOf("</head");
        }
        return index;
    }

    // For those who is using ContentSecurityPolicy tag in their web page,
    // Add our http provider urls to prevent access failure.
    private String handleContentSecurityPolicyTag(String html) {
        String urlPattern = " https://mainnet.infura.io/v3/c9a91b281765454ea61088c5721af84f https://v2.api.trongrid.io ";
        int position = getCspInjectionPosition(html);

        if (position > 0) {
            String beforeTag = html.substring(0, position);
            String afterTag = html.substring(position);

            String result = beforeTag + urlPattern + afterTag;
            return result;
        }
        return html;
    }

    private int getCspInjectionPosition(String body) {
        if (body.indexOf("Content-Security-Policy") < 0) {
            return -1;
        }
        body = body.toLowerCase();
        int cspIndex = body.indexOf("connect-src");
        if (cspIndex < 0) {
            return -1;
        }
        String cspPart = body.substring(cspIndex);
        int lastIndex = cspPart.indexOf(";");
        return cspIndex + lastIndex;
    }
}
