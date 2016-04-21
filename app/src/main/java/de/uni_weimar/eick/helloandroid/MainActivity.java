package de.uni_weimar.eick.helloandroid;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.PowerManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {

    ProgressDialog mConnectDialog;
    Button mConnectButton;
    EditText mUrlText;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mConnectDialog = new ProgressDialog(MainActivity.this);
        mConnectDialog.setIndeterminate(true);
        mConnectDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mConnectDialog.setCancelable(true);
        mConnectDialog.setMessage(getString(R.string.download_dialog_msg));


        mUrlText = (EditText) findViewById(R.id.urlText);

        mConnectButton = (Button) findViewById(R.id.connectButton);
        mConnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // hide soft keyboard after click
                InputMethodManager imm =
                        (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);

                final ConnectTask connectTask = new ConnectTask(MainActivity.this);
                connectTask.execute(mUrlText.getText().toString());

                mConnectDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        connectTask.cancel(true);
                    }
                });
            }
        });
    }

    /*

    AsyncTask to download a web ressource and display a progress dialog uses code from
    SO user Christian (244296):
    http://stackoverflow.com/questions/3028306/download-a-file-with-android-and-showing-the-progress-in-a-progressdialog

     */

    private class ConnectTask extends AsyncTask<String, Integer, String> {
        private Context context;
        private PowerManager.WakeLock mWakeLock;

        private String mContentType;
        private ByteArrayOutputStream mOutputStream;
        private URL mUrl;

        public ConnectTask(Context context) {
            this.context = context;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            // acquire WakeLock in case screen goes off during connection
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());
            mWakeLock.acquire();
            mConnectDialog.show();
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            super.onProgressUpdate(progress);
            // file length is known now - publish progress to dialog
            mConnectDialog.setIndeterminate(false);
            mConnectDialog.setMax(100);
            mConnectDialog.setProgress(progress[0]);
        }

        private void resetContentViews() {
            TextView outputTextview = (TextView) findViewById(R.id.outputTextview);
            ImageView imageView = (ImageView) findViewById(R.id.imageView);
            WebView webView = (WebView) findViewById(R.id.webView);

            outputTextview.setVisibility(View.GONE);
            imageView.setVisibility(View.GONE);
            webView.setVisibility(View.GONE);
        }

        private void displayContent() {
            TextView outputTextview = (TextView) findViewById(R.id.outputTextview);
            ImageView imageView = (ImageView) findViewById(R.id.imageView);
            WebView webView = (WebView) findViewById(R.id.webView);

            // decide which content container we show depending on the ContentType

            if(mContentType.contains("text/plain")) {
                Toast.makeText(context, "Displaying content type text in TextView",
                        Toast.LENGTH_LONG).show();
                outputTextview.setVisibility(View.VISIBLE);
                outputTextview.setText(mOutputStream.toString());
            }
            else if(mContentType.contains("image/")) {
                Toast.makeText(context, "Displaying content type image in ImageView",
                        Toast.LENGTH_LONG).show();
                byte[] byteArray = mOutputStream.toByteArray();
                Bitmap bmp = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length);
                imageView.setVisibility(View.VISIBLE);
                imageView.setImageBitmap(bmp);
            } else if(mContentType.contains("text/html")) {
                Toast.makeText(context, "Loading content type HTML in WebView",
                        Toast.LENGTH_LONG).show();
                webView.setVisibility(View.VISIBLE);
                webView.loadUrl(mUrl.toString());
            } else {
                Toast.makeText(context, "Sorry - I dont know how to display content from type " +
                mContentType, Toast.LENGTH_LONG).show();
            }
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            // clean up
            mWakeLock.release();
            mConnectDialog.dismiss();

            if (result != null) {
                Toast.makeText(context, "Error! " + result, Toast.LENGTH_LONG).show();
                resetContentViews();
            } else {
                Toast.makeText(context, "Finished", Toast.LENGTH_SHORT).show();
                resetContentViews();
                displayContent();
            }
        }

        @Override
        protected String doInBackground(String... sUrl) {
            InputStream input = null;

            HttpURLConnection connection = null;
            try {
                mUrl = new URL(sUrl[0]);
                connection = (HttpURLConnection) mUrl.openConnection();
                connection.connect();

                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    return "Server returned Response Code " + connection.getResponseCode() + " " +
                            connection.getResponseMessage();
                }

                mContentType = connection.getContentType(); // text/plain text/html image/jpeg
                if (mContentType.contains("text/html")) { // we don't need to download this, we load HTML content in WebView
                    return null;
                }

                int fileLength = connection.getContentLength();

                input = connection.getInputStream();
                mOutputStream = new ByteArrayOutputStream();

                byte data[] = new byte[4096];
                long total = 0;
                int count;
                while ((count = input.read(data)) != -1) {
                    if (isCancelled()) {
                        input.close();
                        return null;
                    }
                    total += count;
                    if (fileLength > 0) {
                        publishProgress((int) (total * 100 / fileLength));
                    }
                    mOutputStream.write(data, 0, count);
                }

            } catch (Exception e) {
                return e.toString();
            } finally {
                try {
                    if (mOutputStream != null) {
                        mOutputStream.close();
                    }
                    if (input != null){
                        input.close();
                    }
                } catch (Exception e) {
                    return e.toString();
                }
                if (connection != null) {
                    connection.disconnect();
                }
            }
            return null;
        }
    }
}
