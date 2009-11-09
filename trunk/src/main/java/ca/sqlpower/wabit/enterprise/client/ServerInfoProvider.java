/*
 * Copyright (c) 2009, SQL Power Group Inc.
 *
 * This file is part of Wabit.
 *
 * Wabit is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * Wabit is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>. 
 */

package ca.sqlpower.wabit.enterprise.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;
import org.json.JSONException;
import org.json.JSONObject;

import ca.sqlpower.util.Version;

public abstract class ServerInfoProvider {
	
	public static final String defaultWatermarkMessage = "This version of Wabit is for EVALUATION PURPOSES ONLY. To obtain a full Production License, please visit www.sqlpower.ca/wabit-ep";
	
	private static Map<String,Version> version = new HashMap<String, Version>();
	
	private static Map<String,Boolean> licenses = new HashMap<String, Boolean>();
	
	private static Map<String,String> watermarkMessages = new HashMap<String, String>();
	
	public static Version getServerVersion(
			String host,
			String port,
			String path, 
			String username, 
			String password) throws MalformedURLException,IOException 
	{
		init(toURL(host, port, path), username, password);
		return version.get(toURL(host, port, path).toString().concat(username).concat(password));
	}
	
	public static boolean isServerLicensed(WabitServerInfo infos) 
			throws MalformedURLException,IOException 
	{
		return isServerLicensed(
				infos.getServerAddress(), 
				String.valueOf(infos.getPort()), 
				infos.getPath(), 
				infos.getUsername(), 
				infos.getPassword());
	}

	public static boolean isServerLicensed(
			String host,
			String port,
			String path, 
			String username, 
			String password) throws MalformedURLException,IOException 
	{
		init(toURL(host, port, path), username, password);
		return licenses.get(toURL(host, port, path).toString().concat(username).concat(password));
	}
	
	private static URL toURL(
			String host,
			String port,
			String path) throws MalformedURLException 
	{
		// Build the base URL
		StringBuilder sb = new StringBuilder();
		sb.append("http://");
		sb.append(host);
		sb.append(":");
		sb.append(port);
		sb.append(path);
		sb.append(path.endsWith("/")?"serverinfo":"/serverinfo");
		
		// Spawn a connection object
		return new URL(sb.toString());
		
	}

	private static void init(URL url, String username, String password) throws IOException {
		
		if (version.containsKey(url.toString().concat(username).concat(password))) return;
		
		HttpURLConnection conn = null;
		InputStream is = null;
		ByteArrayOutputStream baos = null;
		try {
			conn = (HttpURLConnection)url.openConnection();
			conn.setRequestMethod("OPTIONS");
			conn.setDoInput(true);
			conn.setConnectTimeout(5000);
			
			// Add credentials
			String hash = new String(
				Base64.encodeBase64(
					username
						.concat(":")
						.concat(password).getBytes()));
			conn.setRequestProperty(
					"Authorization", "Basic " + hash);

			// Get the response
			conn.connect();
			is = conn.getInputStream();
			baos = new ByteArrayOutputStream();
			byte[] buf = new byte[1024];
			int count;
			while ((count = is.read(buf)) > 0) {
				baos.write(buf, 0, count);
			}
			
			// Decode the message
			String serverVersion;
			Boolean licensedServer;
			String watermarkMessage;
			try {
				JSONObject jsonObject = new JSONObject(baos.toString());
				serverVersion = jsonObject.getString(ServerProperties.SERVER_VERSION.toString());
				licensedServer = jsonObject.getBoolean(ServerProperties.SERVER_LICENSED.toString());
				watermarkMessage = jsonObject.getString(ServerProperties.SERVER_WATERMARK_MESSAGE.toString());
			} catch (JSONException e) {
				throw new IOException(e.getMessage());
			}
			
			// Save found values
			version.put(url.toString().concat(username).concat(password), new Version(serverVersion));
			licenses.put(url.toString().concat(username).concat(password), licensedServer);
			watermarkMessages.put(url.toString().concat(username).concat(password), watermarkMessage);
			
		} finally {
			conn.disconnect();
			try {
				if (is != null) is.close();
			} catch (IOException e2) {
				// no op
			}
			try {
				if (baos != null) baos.close();
			} catch (IOException e1) {
				// no op
			}
		}
	}
	
	public static String getWatermarkMessage(WabitServerInfo infos) 
			throws MalformedURLException,IOException 
	{
		return getWatermarkMessage(
				infos.getServerAddress(), 
				String.valueOf(infos.getPort()), 
				infos.getPath(), 
				infos.getUsername(), 
				infos.getPassword());
	}
	
	public static String getWatermarkMessage(
			String host,
			String port,
			String path, 
			String username, 
			String password)
	{
		String message = defaultWatermarkMessage;
		try {
			if (!isServerLicensed(host,port,path,username,password)) {
				message = watermarkMessages.get(toURL(host, port, path).toString().concat(username).concat(password));
			} else {
				message = "";
			}
		} catch (Exception e) {
			// no op
		}
		return message;		
	}
}
