package de.danielweisser.android.plaxosync.client;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolException;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.RedirectHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HttpContext;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import de.danielweisser.android.plaxosync.authenticator.PlaxoAuthenticatorActivity;

/**
 * Provides utility methods for communicating with the server.
 */
public class PlaxoUtilities {
	private static final String TAG = "PlaxoUtilities";

	/**
	 * Executes the network requests on a separate thread.
	 * 
	 * @param runnable
	 *            The runnable instance containing network operations to be executed.
	 */
	public static Thread performOnBackgroundThread(final Runnable runnable) {
		final Thread t = new Thread() {
			@Override
			public void run() {
				try {
					runnable.run();
				} finally {
				}
			}
		};
		t.start();
		return t;
	}

	private static DefaultHttpClient getPlaxoConnection(String username, String password) throws PlaxoLoginException {
		DefaultHttpClient httpclient = null;
		try {
			httpclient = new DefaultHttpClient();
			httpclient.setRedirectHandler(new RedirectHandler() {
				public URI getLocationURI(HttpResponse response, HttpContext context) throws ProtocolException {
					return null;
				}

				public boolean isRedirectRequested(HttpResponse response, HttpContext context) {
					return false;
				}
			});
			httpclient.getCredentialsProvider().setCredentials(new AuthScope(null, -1), new UsernamePasswordCredentials(username, password));
			HttpGet httpget = new HttpGet("http://www.plaxo.com/pdata/contacts/@me/@all?count=1");
			HttpResponse response = httpclient.execute(httpget);
			HttpEntity entity = response.getEntity();

			Log.d(TAG, "Login form get: " + response.getStatusLine());
			if (entity != null) {
				entity.consumeContent();
			}
			if (response.getStatusLine().getStatusCode() != 200) {
				if (response.getStatusLine().getStatusCode() == 401) {
					throw new PlaxoLoginException("Wrong username or password");
				} else {
					throw new PlaxoLoginException("Error on connecting to Plaxo. Error code: " + response.getStatusLine().getStatusCode());
				}
			}
		} catch (ClientProtocolException e) {
			Log.e(TAG, e.getMessage(), e);
			throw new PlaxoLoginException(e.getMessage());
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
			throw new PlaxoLoginException(e.getMessage());
		}
		return httpclient;
	}

	/**
	 * Connects to Plaxo, authenticates the provided username and password.
	 * 
	 * @param username
	 *            The user's username
	 * @param handler
	 *            The handler instance from the calling UI thread.
	 * @param context
	 *            The context of the calling Activity.
	 * @return boolean The boolean result indicating whether the user was successfully authenticated.
	 */
	public static boolean authenticate(String username, String password, Handler handler, final Context context) {
		DefaultHttpClient httpclient = null;
		try {
			httpclient = getPlaxoConnection(username, password);
			if (httpclient != null) {
				sendResult(true, handler, context, null);
				return true;
			}
		} catch (PlaxoLoginException e) {
			Log.e(TAG, e.getMessage(), e);
			sendResult(false, handler, context, e.getMessage());
			return false;
		} finally {
			if (httpclient != null) {
				httpclient.getConnectionManager().shutdown();
			}
		}
		return false;
	}

	/**
	 * Sends the authentication response from server back to the caller main UI thread through its handler.
	 * 
	 * @param result
	 *            The boolean holding authentication result
	 * @param handler
	 *            The main UI thread's handler instance.
	 * @param context
	 *            The caller Activity's context.
	 * @param message
	 */
	private static void sendResult(final Boolean result, final Handler handler, final Context context, final String message) {
		if (handler == null || context == null) {
			return;
		}
		handler.post(new Runnable() {
			public void run() {
				((PlaxoAuthenticatorActivity) context).onAuthenticationResult(result, message);
			}
		});
	}

	/**
	 * Attempts to authenticate the user credentials on the server.
	 * 
	 * @param username
	 *            The user's username
	 * @param password
	 *            The password
	 * @param handler
	 *            The main UI thread's handler instance.
	 * @param context
	 *            The caller Activity's context
	 * @return Thread The thread on which the network mOperations are executed.
	 */
	public static Thread attemptAuth(final String username, final String password, final Handler handler, final Context context) {
		return PlaxoUtilities.performOnBackgroundThread(new Runnable() {
			public void run() {
				authenticate(username, password, handler, context);
			}
		});
	}

	public static List<Contact> fetchContacts(String username, String password, Date mLastUpdated, final Context context) {
		final ArrayList<Contact> friendList = new ArrayList<Contact>();

		DefaultHttpClient httpclient = null;
		try {
			httpclient = getPlaxoConnection(username, password);
			if (httpclient != null) {
				HttpGet httpget = new HttpGet("http://www.plaxo.com/pdata/contacts/@me/@all");
				HttpResponse response = httpclient.execute(httpget);
				HttpEntity entity = response.getEntity();

				if (entity != null) {
					InputStream contentStream = entity.getContent();
					parseJSON(friendList, contentStream);
					entity.consumeContent();
					Log.d(TAG, "Number of contacts: " + friendList.size());
				}
			}
		} catch (PlaxoLoginException e) {
			Log.e(TAG, e.getMessage(), e);
		} catch (ClientProtocolException e) {
			Log.e(TAG, e.getMessage(), e);
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
		} finally {
			if (httpclient != null) {
				httpclient.getConnectionManager().shutdown();
			}
		}

		return friendList;
	}

	/**
	 * Parses the JSON.
	 * 
	 * @param friendList
	 *            List with contacts
	 * @param contentStream
	 *            JSON from HTTP
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	private static void parseJSON(final ArrayList<Contact> friendList, InputStream contentStream) throws FileNotFoundException, IOException {
		Log.d(TAG, "Trying to parse the JSON");
		StringBuilder builder = new StringBuilder();
		BufferedReader inJson = new BufferedReader(new InputStreamReader(contentStream));
		String line;
		while ((line = inJson.readLine()) != null) {
			builder.append(line);
		}

		try {
			JSONObject allData = new JSONObject(builder.toString());
			JSONArray jsonArray = allData.getJSONArray("entry");
			for (int i = 0; i < jsonArray.length(); i++) {
				Contact u = Contact.valueOf(jsonArray.getJSONObject(i));
				if (u != null && u.getFirstName() != null && u.getLastName() != null) {
					friendList.add(u);
				}
			}
		} catch (JSONException e) {
			Log.e(TAG, e.getMessage(), e);
		}

		inJson.close();
	}
}
