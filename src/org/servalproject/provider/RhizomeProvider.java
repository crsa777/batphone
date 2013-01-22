package org.servalproject.provider;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.List;

import org.servalproject.rhizome.Rhizome;
import org.servalproject.rhizome.RhizomeManifest;
import org.servalproject.rhizome.RhizomeManifest_File;
import org.servalproject.servald.BundleId;
import org.servalproject.servald.Identity;
import org.servalproject.servald.ServalD;
import org.servalproject.servald.ServalD.RhizomeAddFileResult;
import org.servalproject.servald.SubscriberId;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

public class RhizomeProvider extends ContentProvider {
	public static final String AUTHORITY = "org.servalproject.files";
	private static final String TAG = "RhizomeProvider";

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		Log.v(TAG, "delete " + uri);
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public String getType(Uri uri) {
		Log.v(TAG, "getType " + uri);
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		Log.v(TAG, "insert " + uri);
		try {
			File payloadFile = null;
			File tempManifest = null;
			RhizomeManifest manifest = null;
			SubscriberId author = Identity.getMainIdentity().sid;

			String filePath = values.getAsString("path");
			String manifestPath = values.getAsString("manifest");
			String authorSid = values.getAsString("author");
			Long version = values.getAsLong("version");
			Long date = values.getAsLong("date");
			String name = values.getAsString("name");
			String saveManifestPath = values.getAsString("save_manifest");

			if (manifestPath != null) {
				File manifestFile = new File(manifestPath);
				if (!manifestFile.exists())
					throw new UnsupportedOperationException(
							"Existing manifest file could not be read");
				manifest = RhizomeManifest.readFromFile(manifestFile);
				manifest.unsetFilehash();
				manifest.unsetFilesize();
				manifest.unsetDateMillis();
			}

			if (filePath != null) {
				payloadFile = new File(filePath);
				if (!payloadFile.exists())
					throw new UnsupportedOperationException(
							"Payload file could not be read");
			}

			if (authorSid != null) {
				if (authorSid.equals("")) {
					author = null;
				} else {
					author = new SubscriberId(authorSid);
				}
			}

			if (version != null) {
				if (manifest == null)
					manifest = new RhizomeManifest_File();
				manifest.setVersion(version);
			}

			if (date != null) {
				if (manifest == null)
					manifest = new RhizomeManifest_File();
				manifest.setDateMillis(date);
			}

			if (name != null
					&& (manifest == null || manifest instanceof RhizomeManifest_File)) {
				if (manifest == null)
					manifest = new RhizomeManifest_File();
				((RhizomeManifest_File) manifest).setName(name);
			}

			if (manifest != null) {
				// save to a temporary location
				tempManifest = File.createTempFile("manifest", ".temp",
						Rhizome.getTempDirectoryCreated());
				tempManifest.deleteOnExit();
				manifest.writeTo(tempManifest);
			}

			RhizomeAddFileResult result = ServalD.rhizomeAddFile(
					payloadFile,
					tempManifest, author, null);

			if (tempManifest != null)
				tempManifest.delete();

			if (saveManifestPath != null) {
				// save the new manifest here, so the caller can use it to
				// update a file
				tempManifest = new File(saveManifestPath);
				ServalD.rhizomeExtractManifest(result.manifestId,
						tempManifest);
			}

			return Uri.parse("content://" + AUTHORITY + "/"
					+ result.manifestId.toHex());
		} catch (UnsupportedOperationException e) {
			throw e;
		} catch (Exception e) {
			throw new UnsupportedOperationException(e.getMessage(), e);
		}
	}

	@Override
	public boolean onCreate() {
		return true;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		Log.v(TAG, "query for uri; " + uri +
				", projection; " + Arrays.toString(projection) +
				", selection; " + selection +
				", selectionArgs; " + Arrays.toString(selectionArgs) +
				", sortOrder; " + sortOrder);

		if (projection != null || selection != null
				|| (!uri.getPath().equals("/"))) {
			throw new UnsupportedOperationException("Not implemented");
		}
		try {
			return ServalD.rhizomeList(selectionArgs);
		} catch (Exception e) {
			throw new IllegalArgumentException(e.getMessage(), e);
		}
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs) {
		Log.v("RhizomeProvider", "update " + uri);
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public ParcelFileDescriptor openFile(Uri uri, String mode)
			throws FileNotFoundException {
		Log.v("RhizomeProvider", "openFile " + uri);
		if (mode.indexOf('w') > 0)
			throw new SecurityException("Write operations are not allowed");

		try {
			List<String> segments = uri.getPathSegments();
			if (segments.size() < 1)
				throw new FileNotFoundException();

			BundleId bid = new BundleId(segments.get(0));
			File dir = Rhizome.getTempDirectoryCreated();
			File temp = new File(dir, bid.toString() + ".tmp");
			ServalD.rhizomeExtractFile(bid, temp);
			ParcelFileDescriptor fd = ParcelFileDescriptor.open(temp,
					ParcelFileDescriptor.MODE_READ_ONLY);
			temp.delete();
			return fd;
		} catch (FileNotFoundException e) {
			Log.e("RhizomeProvider", e.getMessage(), e);
			throw e;
		} catch (Exception e) {
			Log.e("RhizomeProvider", e.getMessage(), e);
			FileNotFoundException f = new FileNotFoundException(e.getMessage());
			f.initCause(e);
			throw f;
		}
	}
}