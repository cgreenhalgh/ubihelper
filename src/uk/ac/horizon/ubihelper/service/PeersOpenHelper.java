/**
 * 
 */
package uk.ac.horizon.ubihelper.service;

import org.json.JSONException;
import org.json.JSONObject;

import uk.ac.horizon.ubihelper.protocol.PeerInfo;
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

	static final String PEER_TABLE_NAME = "peer";
	//static final String GROUP_TABLE_NAME = "group";
	//static final String PEERGROUP_TABLE_NAME = "peergroup";

	static final String KEY_NICKNAME = "nickname";
	static final String KEY_NAME = "name";
	static final String KEY_INFO = "info";
	static final String KEY_WIFIMAC = "wifimac";
	static final String KEY_BTMAC = "btmac";
	static final String KEY_IMEI = "imei";
	static final String KEY_ID = "id";
	static final String KEY_SECRET = "secret";
	static final String KEY_CREATED_TIMESTAMP = "created_timestamp";
	static final String KEY_IP_TIMESTAMP = "ip_timestamp";
	static final String KEY_IP = "ip";
	static final String KEY_PORT = "port";
	static final String KEY_PORT_TIMESTAMP = "port_timestamp";
	//static final String KEY_GROUPNAME = "groupname";
	static final String KEY_ROW_ID = BaseColumns._ID;
	static final String KEY_TRUSTED = "trusted";
	static final String KEY_ENABLED = "enabled";
	//static final String KEY_GROUPID = "groupid";
	//static final String KEY_PEERID = "groupid";
	static final String PEER_TABLE_COLUMNS [] = new String[] {
		KEY_ROW_ID, KEY_NICKNAME, KEY_ID, KEY_NAME, KEY_INFO, KEY_WIFIMAC,
		KEY_BTMAC, KEY_IMEI, KEY_SECRET, KEY_CREATED_TIMESTAMP, KEY_IP,
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
					pi = new PeerInfo();
					c.moveToFirst();
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
					//			KEY_IP,
					pi.ip = c.getString(10);
					//			KEY_IP_TIMESTAMP, 
					pi.ipTimestamp = c.getLong(11);
					//			KEY_PORT, 
					pi.port = c.getInt(12);
					//			KEY_PORT_TIMESTAMP, 
					pi.portTimestamp = c.getLong(13);
					//			KEY_TRUSTED, 
					pi.trusted = c.getInt(14)!=0;
					//			KEY_ENABLED
					pi.enabled = c.getInt(15)!=0;
				}
			}
			catch(Exception e) {
				Log.w(TAG,"Error creating PeerInfo from cursor: "+e);
			}
			finally {
				c.close();
			}
		} catch (Exception e) {
			Log.w(TAG,"Error looking for PeerInfo id="+id+": "+e);			
		}
		return pi;
	}
	public static void addPeerInfo(SQLiteDatabase database, PeerInfo pi) {
		// TODO Auto-generated method stub
		XX;
	}
	public static void updatePeerInfo(SQLiteDatabase database, PeerInfo pi) {
		// TODO Auto-generated method stub
		XX;
	}
}
