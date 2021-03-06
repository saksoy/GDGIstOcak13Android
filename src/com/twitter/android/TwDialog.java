/*
 * Copyright 2010 Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twitter.android;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;

import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import oauth.signpost.commonshttp.CommonsHttpOAuthProvider;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;
import oauth.signpost.exception.OAuthNotAuthorizedException;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Display;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.twitter.android.Twitter.TweetDialogListener;

@SuppressLint("SetJavaScriptEnabled")
@SuppressWarnings("deprecation")
public class TwDialog extends Dialog {

	public static final String TAG = "twitter";

	static final int TW_BLUE = 0xFFC0DEED;
	static final float[] DIMENSIONS_LANDSCAPE = { 460, 260 };
	static final float[] DIMENSIONS_PORTRAIT = { 280, 420 };

	static final FrameLayout.LayoutParams FILL = new FrameLayout.LayoutParams(
			ViewGroup.LayoutParams.FILL_PARENT,
			ViewGroup.LayoutParams.FILL_PARENT);
	static final int MARGIN = 4;
	static final int PADDING = 2;

	private int mIcon;
	private String mUrl;
	private TweetDialogListener mListener;
	private ProgressDialog mSpinner;
	private WebView mWebView;
	private LinearLayout mContent;
	private TextView mTitle;
	private Handler mHandler;

	private CommonsHttpOAuthConsumer mConsumer;
	private CommonsHttpOAuthProvider mProvider;

	public TwDialog(Context context, CommonsHttpOAuthProvider provider,
			CommonsHttpOAuthConsumer consumer, TweetDialogListener listener,
			int icon) {
		super(context);
		mProvider = provider;
		mConsumer = consumer;
		mListener = listener;
		mIcon = icon;
		mHandler = new Handler();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mSpinner = new ProgressDialog(getContext());
		mSpinner.requestWindowFeature(Window.FEATURE_NO_TITLE);
		mSpinner.setMessage("Loading...");

		mContent = new LinearLayout(getContext());
		mContent.setOrientation(LinearLayout.VERTICAL);
		setUpTitle();
		setUpWebView();

		Display display = getWindow().getWindowManager().getDefaultDisplay();
		final float scale = getContext().getResources().getDisplayMetrics().density;
		float[] dimensions = display.getWidth() < display.getHeight() ? DIMENSIONS_PORTRAIT
				: DIMENSIONS_LANDSCAPE;
		addContentView(mContent, new FrameLayout.LayoutParams(
				(int) (dimensions[0] * scale + 0.5f), (int) (dimensions[1]
						* scale + 0.5f)));

		retrieveRequestToken();
	}

	@Override
	public void show() {
		super.show();
		mSpinner.show();
	}

	private void setUpTitle() {
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		Drawable icon = getContext().getResources().getDrawable(mIcon);
		mTitle = new TextView(getContext());
		mTitle.setText("Twitter");
		mTitle.setTextColor(Color.WHITE);
		mTitle.setTypeface(Typeface.DEFAULT_BOLD);
		mTitle.setBackgroundColor(TW_BLUE);
		mTitle.setPadding(MARGIN + PADDING, MARGIN, MARGIN, MARGIN);
		mTitle.setCompoundDrawablePadding(MARGIN + PADDING);
		mTitle.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
		mContent.addView(mTitle);
	}

	private void retrieveRequestToken() {
		mSpinner.show();
		new Thread() {
			@Override
			public void run() {
				try {
					mUrl = mProvider.retrieveRequestToken(mConsumer,
							Twitter.CALLBACK_URI);
					mWebView.loadUrl(mUrl);
				} catch (OAuthMessageSignerException e) {
					mListener.onError(new DialogError(e.getMessage(), -1,
							Twitter.OAUTH_REQUEST_TOKEN));
				} catch (OAuthNotAuthorizedException e) {
					mListener.onError(new DialogError(e.getMessage(), -1,
							Twitter.OAUTH_REQUEST_TOKEN));
				} catch (OAuthExpectationFailedException e) {
					mListener.onError(new DialogError(e.getMessage(), -1,
							Twitter.OAUTH_REQUEST_TOKEN));
				} catch (OAuthCommunicationException e) {
					mListener.onError(new DialogError(e.getMessage(), -1,
							Twitter.OAUTH_REQUEST_TOKEN));
				}
			}
		}.start();
	}

	private void retrieveAccessToken(final String url) {
		mSpinner.show();
		new Thread() {
			@Override
			public void run() {
				Uri uri = Uri.parse(url);
				String verifier = uri
						.getQueryParameter(oauth.signpost.OAuth.OAUTH_VERIFIER);
				final Bundle values = new Bundle();
				try {
					mProvider.retrieveAccessToken(mConsumer, verifier);
					values.putString(Twitter.ACCESS_TOKEN, mConsumer.getToken());
					values.putString(Twitter.SECRET_TOKEN,
							mConsumer.getTokenSecret());
					mListener.onComplete(values);
				} catch (OAuthMessageSignerException e) {
					mListener.onError(new DialogError(e.getMessage(), -1,
							verifier));
				} catch (OAuthNotAuthorizedException e) {
					mListener.onTwitterError(new TwitterError(e.getMessage()));
				} catch (OAuthExpectationFailedException e) {
					mListener.onTwitterError(new TwitterError(e.getMessage()));
				} catch (OAuthCommunicationException e) {
					mListener.onError(new DialogError(e.getMessage(), -1,
							verifier));
				}
				mHandler.post(new Runnable() {
					public void run() {
						mSpinner.dismiss();
						TwDialog.this.dismiss();
					}
				});
			}
		}.start();
	}

	private void setUpWebView() {
		mWebView = new WebView(getContext());
		mWebView.setVerticalScrollBarEnabled(false);
		mWebView.setHorizontalScrollBarEnabled(false);
		mWebView.setWebViewClient(new TwDialog.TwWebViewClient());
		mWebView.getSettings().setJavaScriptEnabled(true);
		mWebView.addJavascriptInterface(new MyJavaScriptInterface(), "HTMLOUT");
		mWebView.setLayoutParams(FILL);
		mContent.addView(mWebView);
	}

	private class TwWebViewClient extends WebViewClient {

		@Override
		public boolean shouldOverrideUrlLoading(WebView view, String url) {
			Log.d(TAG, "Redirect URL: " + url);
			if (url.startsWith(Twitter.CALLBACK_URI)) {
				retrieveAccessToken(url);
				return true;
			} else if (url.startsWith(Twitter.CANCEL_URI)) {
				mListener.onCancel();
				TwDialog.this.dismiss();
				return true;
			}
			// launch non-dialog URLs in a full browser
			getContext().startActivity(
					new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
			return true;
		}

		@Override
		public void onReceivedError(WebView view, int errorCode,
				String description, String failingUrl) {
			super.onReceivedError(view, errorCode, description, failingUrl);
			mListener.onError(new DialogError(description, errorCode,
					failingUrl));
			TwDialog.this.dismiss();
		}

		@SuppressWarnings("unused")
		public String convertStreamToString(InputStream is) throws IOException {
			/**
			 * To convert the InputStream to String we use the
			 * Reader.read(char[] buffer) method. We iterate until the Reader
			 * return -1 which means there's no more data to read. We use the
			 * StringWriter class to produce the string.
			 */
			if (is != null) {
				Writer writer = new StringWriter();

				char[] buffer = new char[1024 * 8];
				try {
					Reader reader = new BufferedReader(new InputStreamReader(
							is, "UTF-8"));
					int n;
					while ((n = reader.read(buffer)) != -1) {
						writer.write(buffer, 0, n);
					}
				} finally {
					is.close();
				}
				return writer.toString();
			} else {
				return "";
			}
		}

		@Override
		public void onPageStarted(WebView view, String url, Bitmap favicon) {
			// try {
			// InputStream is = (InputStream) new URL(url).getContent();
			// System.out.println("HTML string : "+convertStreamToString(is));
			// } catch (MalformedURLException e) {
			// // TODO Auto-generated catch block
			// e.printStackTrace();
			// } catch (IOException e) {
			// // TODO Auto-generated catch block
			// e.printStackTrace();
			// }

			Log.d(TAG, "WebView loading URL: " + url);
			// if(url.equals("https://api.twitter.com/oauth/authorize")){
			// try {
			// mWebView.loadUrl("http://www.sociga.me/");
			// } catch (Exception e) {
			// e.printStackTrace();
			// }
			// System.out.println("Token : "+Twitter.ACCESS_TOKEN);
			// System.out.println("Secret : "+Twitter.SECRET_TOKEN);
			// TwDialog.this.dismiss();
			// }

			super.onPageStarted(view, url, favicon);
			if (mSpinner.isShowing()) {
				mSpinner.dismiss();
			}
			mSpinner.show();
		}

		@Override
		public void onPageFinished(WebView view, String url) {
			super.onPageFinished(view, url);

			if (url.equals("https://api.twitter.com/oauth/authorize")) {
				Uri uri = Uri.parse(url);
				String verifier = uri
						.getQueryParameter(oauth.signpost.OAuth.OAUTH_VERIFIER);
				final Bundle values = new Bundle();
				try {
					mProvider.retrieveAccessToken(mConsumer, verifier);
					values.putString(Twitter.ACCESS_TOKEN, mConsumer.getToken());
					values.putString(Twitter.SECRET_TOKEN,
							mConsumer.getTokenSecret());
					mListener.onComplete(values);
				} catch (OAuthMessageSignerException e) {
					mListener.onError(new DialogError(e.getMessage(), -1,
							verifier));
				} catch (OAuthNotAuthorizedException e) {
					mListener.onTwitterError(new TwitterError(e.getMessage()));
				} catch (OAuthExpectationFailedException e) {
					mListener.onTwitterError(new TwitterError(e.getMessage()));
				} catch (OAuthCommunicationException e) {
					mListener.onError(new DialogError(e.getMessage(), -1,
							verifier));
				}
				mHandler.post(new Runnable() {
					public void run() {
						mSpinner.dismiss();
						TwDialog.this.dismiss();
					}
				});
			}

			String title = mWebView.getTitle();
			if (title != null && title.length() > 0) {
				mTitle.setText(title);
			}
			mSpinner.dismiss();

		}
	}

	class MyJavaScriptInterface {
		public void showHTML(String html) {
			System.out.println("Pin code : " + html);
			// new AlertDialog.Builder()
			// .setTitle("HTML")
			// .setMessage(html)
			// .setPositiveButton(android.R.string.ok, null)
			// .setCancelable(false)
			// .create()
			// .show();
		}
	}
}
