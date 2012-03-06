/**
 * 
 */
package uk.ac.horizon.ubihelper.service;

import java.util.LinkedList;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

import uk.ac.horizon.ubihelper.protocol.PeerInfo;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.util.Log;

/** 
 * @author cmg
 *
 */
public class PeersOpenHelper extends SQLiteOpenHelper {
	private static final int DATABASE_VERSION = 1;
	private static final String DATABASE_NAME = "peers";

	public static final String PEER_TABLE_NAME = "peer";
	//static final String GROUP_TABLE_NAME = "group";
	//static final String PEERGROUP_TABLE_NAME = "peergroup";

	public static final String KEY_NICKNAME = "nickname";
	public static final String KEY_NAME = "name";
	public static final String KEY_INFO = "info";
	public static final String KEY_WIFIMAC = "wifimac";
	public static final String KEY_BTMAC = "btmac";
	public static final String KEY_IMEI = "imei";
	public static final String KEY_ID = "id";
	public static final String KEY_SECRET = "secret";
	public static final String KEY_CREATED_TIMESTAMP = "created_timestamp";
	public static final String KEY_MANUAL = "manual";
	public static final String KEY_IP_TIMESTAMP = "ip_timestamp";
	public static final String KEY_IP = "ip";
	public static final String KEY_PORT = "port";
	public static final String KEY_PORT_TIMESTAMP = "port_timestamp";
	//static final String KEY_GROUPNAME = "groupname";
	public static final String KEY_ROW_ID = BaseColumns._ID;
	public static final String KEY_TRUSTED = "trusted";
	public static final String KEY_ENABLED = "enabled";
	//static final String KEY_GROUPID = "groupid";
	//static final String KEY_PEERID = "groupid";
	static final String PEER_TABLE_COLUMNS [] = new String[] {
		KEY_ROW_ID, KEY_NICKNAME, KEY_ID, KEY_NAME, KEY_INFO, KEY_WIFIMAC,
		KEY_BTMAC, KEY_IMEI, KEY_SECRET, KEY_CREATED_TIMESTAMP, KEY_MANUAL, KEY_IP,
		KEY_IP_TIMESTAMP, KEY_PORT, KEY_PORT_TIMESTAMP, KEY_TRUSTED, KEY_ENABLED
	};
	private static final String PEER_TABLE_CREATE =
			"CREATE TABLE " + PEER_TABLE_NAME + " (" +
					KEY_ROW_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
					KEY_NICKNAME + " TEXT, "+
					KEY_ID + " TEXT, "+
					KEY_NAME + " TEXT, "+
					KEY_INFO + " TEXT, "+
					KEY_WIFIMAC + " TEXT, "+
					KEY_BTMAC + " TEXT, "+
					KEY_IMEI + " TEXT, "+
					KEY_SECRET + " TEXT, "+
					KEY_CREATED_TIMESTAMP + " INTEGER, "+
					KEY_MANUAL + " INTEGER, "+
					KEY_IP + " TEXT, "+
					KEY_IP_TIMESTAMP + " INTEGER, "+
					KEY_PORT + " INTEGER, "+
					KEY_PORT_TIMESTAMP + " INTEGER, "+
					KEY_TRUSTED + " INTEGER, "+
					KEY_ENABLED + " INTEGER);";
	private static final String TAG = "ubihelper-db";
//	private static final String GROUP_TABLE_CREATE =
//			"CREATE TABLE " + GROUP_TABLE_NAME + " (" +
//					KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
//					KEY_NAME + " TEXT, "+
//					KEY_TRUSTED + " INTEGER);";
//	private static final String PEERGROUP_TABLE_CREATE = 
//			"CREATE TABLE " + PEERGROUP_TABLE_NAME + " (" +
//					KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
//					KEY_GROUPID + " INTEGER, "+
//					KEY_PEERID + " INTEGER);";
	public PeersOpenHelper(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}
	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL(PEER_TABLE_CREATE);
//		db.execSQL(GROUP_TABLE_CREATE);
//		db.execSQL(PEERGROUP_TABLE_CREATE);
	}

	@Override
	public void onUpgrade(SQLiteDatabase arg0, int arg1, int arg2) {
		// ...
	}

	public static PeerInfo getPeerInfo(SQLiteDatabase db, String id) {
		PeerInfo pi = null;
		try {
			Cursor c = db.query(PEER_TABLE_NAME, PEER_TABLE_COLUMNS, "ID = ?", new String [] { id }, null, null, null);
			try {
				if (c.getCount()>0) {
					c.moveToFirst();
					pi = getPeerInfo(c);
				}
			}
			finally {
				c.close();
			}
		} catch (Exception e) {
			Log.w(TAG,"Error looking for PeerInfo id="+id+": "+e);			
		}
		return pi;
	}
	public static PeerInfo getPeerInfo(Cursor c) {
		PeerInfo pi = new PeerInfo();
		try {
			// coordinate with PEER_TABLE_COLUMNS!
			//			KEY_ROW_ID, 
			pi._id = c.getInt(0);
			//			KEY_NICKNAME, 
			pi.nickname = c.getString(1);
			//			KEY_ID, 
			pi.id = c.getString(2);
			//			KEY_NAME, 
			pi.name = c.getString(3);
			//			KEY_INFO, 
			String info = c.getString(4);
			try {
				if (info!=null)
					pi.info = new JSONObject(info);
			}
			catch (JSONException e) {
				Log.w(TAG,"Error parsing info: "+e+" ("+info+")");
			}
			//			KEY_WIFIMAC,
			pi.wifimac = c.getString(5);
			//			KEY_BTMAC, 
			pi.btmac = c.getString(6);
			//			KEY_IMEI, 
			pi.imei = c.getString(7);
			//			KEY_SECRET, 
			pi.secret = c.getString(8);
			//			KEY_CREATED_TIMESTAMP, 
			pi.createdTimestamp = c.getLong(9);
			//			KEY_MANUAL, 
			pi.manual = c.getInt(10)!=0;
			//			KEY_IP,
			pi.ip = c.getString(11);
			//			KEY_IP_TIMESTAMP, 
			pi.ipTimestamp = c.getLong(12);
			//			KEY_PORT, 
			pi.port = c.getInt(13);
			//			KEY_PORT_TIMESTAMP, 
			pi.portTimestamp = c.getLong(14);
			//			KEY_TRUSTED, 
			pi.trusted = c.getInt(15)!=0;
			//			KEY_ENABLED
			pi.enabled = c.getInt(16)!=0;
		} catch (Exception e) {
			Log.e(TAG,"Error converting row to PeerInfo: "+e);
		}
		return pi;
	}
	public static ContentValues getContentValues(PeerInfo pi)  {
		ContentValues values = new ContentValues();
//		KEY_ROW_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
//		KEY_NICKNAME + " TEXT, "+
		values.put(KEY_NICKNAME, pi.nickname);
//		KEY_ID + " TEXT, "+
		values.put(KEY_ID, pi.id);
//		KEY_NAME + " TEXT, "+
		values.put(KEY_NAME, pi.name);
//		KEY_INFO + " TEXT, "+
		if (pi.info!=null)
			values.put(KEY_INFO, pi.info.toString());
//		KEY_WIFIMAC + " TEXT, "+
		values.put(KEY_WIFIMAC, pi.wifimac);
//		KEY_BTMAC + " TEXT, "+
		values.put(KEY_BTMAC, pi.btmac);
//		KEY_IMEI + " TEXT, "+
		values.put(KEY_IMEI, pi.imei);
//		KEY_SECRET + " TEXT, "+
		values.put(KEY_SECRET, pi.secret);
//		KEY_CREATED_TIMESTAMP + " INTEGER, "+
		values.put(KEY_CREATED_TIMESTAMP, pi.createdTimestamp);
//		KEY_MANUAL + " INTEGER, "+
		values.put(KEY_MANUAL, pi.manual ? 1 : 0);
//		KEY_IP + " TEXT, "+
		values.put(KEY_IP, pi.ip);
//		KEY_IP_TIMESTAMP + " INTEGER, "+
		values.put(KEY_IP_TIMESTAMP, pi.ipTimestamp);
//		KEY_PORT + " INTEGER, "+
		values.put(KEY_PORT, pi.port);
//		KEY_PORT_TIMESTAMP + " INTEGER, "+
		values.put(KEY_PORT_TIMESTAMP, pi.portTimestamp);
//		KEY_TRUSTED + " INTEGER, "+
		values.put(KEY_TRUSTED, pi.trusted ? 1 : 0);
//		KEY_ENABLED + " INTEGER);";
		values.put(KEY_ENABLED, pi.enabled ? 1 : 0);
		return values;
	}
	public static long addPeerInfo(SQLiteDatabase database, PeerInfo pi) {		
		pi._id = database.insert(PEER_TABLE_NAME, null, getContentValues(pi));
		if (pi._id==(-1)) {
			Log.e(TAG,"Error adding PeerInfo "+pi.id);
		}
		return pi._id;
	}
	public static boolean updatePeerInfo(SQLiteDatabase database, PeerInfo pi) {
		int rval = database.update(PEER_TABLE_NAME, getContentValues(pi), KEY_ROW_ID+" = ?", new String[] { Long.toString(pi._id) } );
		if (rval==0) {
			Log.e(TAG,"Error updating PeerInfo "+pi._id);
			return false;
		}
		else
			Log.d(TAG,"Updated PeerInfo "+pi._id);
		return true;
	}
	public static Cursor getPeerCursor(SQLiteDatabase database) {
		return database.query(PEER_TABLE_NAME, PEER_TABLE_COLUMNS, null, null, null, null, null);
	}
	public static List<PeerInfo> getPeerInfos(SQLiteDatabase database) {
		return getPeerInfos(database, null, null);
	}
	public static List<PeerInfo> getPeerInfos(SQLiteDatabase database, String where, String values[]) {
		Cursor c = database.query(PEER_TABLE_NAME, PEER_TABLE_COLUMNS, where, values, null, null, KEY_NAME+" ASC");
		LinkedList<PeerInfo> pis = new LinkedList<PeerInfo>();
		Log.d(TAG,"getPeerInfos returned "+c.getCount()+" rows");
		while (c.move(1)) {
			pis.add(getPeerInfo(c));
		}
		c.close();
		return pis;
	}
}
