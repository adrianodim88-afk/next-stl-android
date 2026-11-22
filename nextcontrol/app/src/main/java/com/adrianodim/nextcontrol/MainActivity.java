package com.adrianodim.nextcontrol;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;

public class MainActivity extends Activity {

    // Abre direto o seu sistema de controle (mesmo site do Vercel).
    private static final String APP_URL = "https://nextcontrol-eight.vercel.app/";

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private static final int REQ_FILE = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportMultipleWindows(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        // Ponte para salvar/compartilhar PDF e PNG gerados no sistema (o navegador
        // interno não baixa arquivos sozinho).
        web.addJavascriptInterface(new SaveBridge(), "AndroidSave");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // Mantém a navegação dentro do app.
                return false;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = cb;
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");
                try {
                    startActivityForResult(Intent.createChooser(intent, "Selecionar imagem"), REQ_FILE);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl(APP_URL);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE) {
            if (fileCallback == null) return;
            Uri[] result = null;
            if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
                result = new Uri[]{ data.getData() };
            }
            fileCallback.onReceiveValue(result);
            fileCallback = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (web != null) web.saveState(outState);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    // Recebe o arquivo (PDF/PNG) do sistema como data URL, salva e abre o menu
    // de compartilhar/salvar (WhatsApp, Drive, Arquivos, etc.).
    public class SaveBridge {
        @JavascriptInterface
        public void save(final String filename, final String dataUrl) {
            try {
                int comma = dataUrl.indexOf(',');
                String meta = dataUrl.substring(0, comma);
                byte[] bytes = Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT);
                String mime = "application/octet-stream";
                int c = meta.indexOf(':'), sc = meta.indexOf(';');
                if (c >= 0 && sc > c) mime = meta.substring(c + 1, sc);

                File dir = new File(getExternalFilesDir(null), "Relatorios");
                if (!dir.exists()) dir.mkdirs();
                String nome = (filename == null || filename.isEmpty()) ? "Adriano_Dim.pdf" : filename;
                final File out = new File(dir, nome);
                FileOutputStream fos = new FileOutputStream(out);
                fos.write(bytes);
                fos.close();

                final Uri uri = FileProvider.getUriForFile(
                        MainActivity.this, getPackageName() + ".fileprovider", out);
                final String fmime = mime;
                runOnUiThread(new Runnable() {
                    public void run() {
                        try {
                            Intent send = new Intent(Intent.ACTION_SEND);
                            send.setType(fmime);
                            send.putExtra(Intent.EXTRA_STREAM, uri);
                            send.putExtra(Intent.EXTRA_SUBJECT, out.getName());
                            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            startActivity(Intent.createChooser(send, "Salvar ou enviar: " + out.getName()));
                        } catch (Exception e) {
                            Toast.makeText(MainActivity.this, "Salvo em Arquivos do app: " + out.getName(), Toast.LENGTH_LONG).show();
                        }
                    }
                });
            } catch (final Exception e) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(MainActivity.this, "Não consegui salvar: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            }
        }
    }
}
