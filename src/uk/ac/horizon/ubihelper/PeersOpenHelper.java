/**
 * 
 */
package uk.ac.horizon.ubihelper;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;

/** 
 * @author cmg
 *
 */
public class PeersOpenHelper extends SQLiteOpenHelper {
	private static final int DATABASE_VERSION = 1;
	private static final String DATABASE_NAME = "peers";

	static final String PEER_TABLE_NAME = "peer";
	static final String GROUP_TABLE_NAME = "group";
	static final String PEERGROUP_TABLE_NAME = "peergroup";

	static final String KEY_NICKNAME = "nickname";
	static final String KEY_NAME = "name";
	static final String KEY_WIFIMAC = "wifimac";
	static final String KEY_BTMAC = "btmac";
	static final String KEY_IP = "ip";
	static final String KEY_IMEI = "imei";
	static final String KEY_GROUPNAME = "groupname";
	static final String KEY_ID = BaseColumns._ID;
	static final String KEY_META = "meta";
	static final String KEY_TRUSTED = "trusted";
	static final String KEY_GROUPID = "groupid";
	static final String KEY_PEERID = "groupid";
	
	private static final String PEER_TABLE_CREATE =
			"CREATE TABLE " + PEER_TABLE_NAME + " (" +
					KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
					KEY_NICKNAME + " TEXT, "+
					KEY_NAME + " TEXT, "+
					KEY_WIFIMAC + " TEXT, "+
					KEY_BTMAC + " TEXT, "+
					KEY_IP + " TEXT, "+
					KEY_IMEI + " TEXT, "+
					KEY_META + " TEXT, "+
					KEY_TRUSTED + " INTEGER);";
	private static final String GROUP_TABLE_CREATE =
			"CREATE TABLE " + GROUP_TABLE_NAME + " (" +
					KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
					KEY_NAME + " TEXT, "+
					KEY_TRUSTED + " INTEGER);";
	private static final String PEERGROUP_TABLE_CREATE = 
			"CREATE TABLE " + PEERGROUP_TABLE_NAME + " (" +
					KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
					KEY_GROUPID + " INTEGER, "+
					KEY_PEERID + " INTEGER);";
	public PeersOpenHelper(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}
	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL(PEER_TABLE_CREATE);
		db.execSQL(GROUP_TABLE_CREATE);
		db.execSQL(PEERGROUP_TABLE_CREATE);
	}

	@Override
	public void onUpgrade(SQLiteDatabase arg0, int arg1, int arg2) {
		// ...
	}

}
