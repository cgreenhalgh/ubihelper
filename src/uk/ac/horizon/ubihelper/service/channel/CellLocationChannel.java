/**
 * 
 */
package uk.ac.horizon.ubihelper.service.channel;

import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Service;
import android.os.Handler;
import android.telephony.CellLocation;
import android.telephony.NeighboringCellInfo;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;

/**
 * @author cmg
 *
 */
public class CellLocationChannel extends PollingChannel {
	private boolean includeNeighbours;
	private TelephonyManager telephony;
	/**
	 * @param handler
	 * @param name
	 */
	public CellLocationChannel(Handler handler, Service service, String name, boolean includeNeighbours) {
		super(handler, name);
		this.includeNeighbours = includeNeighbours;
		telephony = (TelephonyManager)service.getSystemService(Service.TELEPHONY_SERVICE);
	}

	@Override
	protected boolean startPoll() {
		if (telephony!=null) {
			CellLocation loc = telephony.getCellLocation();
			JSONObject value = new JSONObject();
			try {
				value.put("timestamp", System.currentTimeMillis());
				if (loc instanceof GsmCellLocation) {
					GsmCellLocation gsm = (GsmCellLocation)loc;
					if (gsm.getCid()!=(-1))
						value.put("cid", gsm.getCid());
					if (gsm.getLac()!=(-1))
						value.put("lac", gsm.getLac());
					value.put("type","gsm");
				}
				else if (loc instanceof CdmaCellLocation) {
					CdmaCellLocation cdma = (CdmaCellLocation)loc;
					if (cdma.getBaseStationId()!=(-1))
						value.put("baseStationId", cdma.getBaseStationId());
					if (cdma.getBaseStationLatitude()!=Integer.MAX_VALUE)
						value.put("baseStationLat", cdma.getBaseStationLatitude());
					if (cdma.getBaseStationLongitude()!=Integer.MAX_VALUE)
						value.put("baseStationLon", cdma.getBaseStationLongitude());
					if (cdma.getNetworkId()!=(-1))
						value.put("baseStationId", cdma.getNetworkId());
					if (cdma.getNetworkId()!=(-1))
						value.put("networkId", cdma.getNetworkId());
					if (cdma.getSystemId()!=(-1))
						value.put("systemId", cdma.getSystemId());
					value.put("type","cdma");
				}
				else if (loc!=null) {
					value.put("type", loc.getClass().getName());
				}
				if (includeNeighbours) {
					List<NeighboringCellInfo> neighbors = telephony.getNeighboringCellInfo();
					JSONArray ns = new JSONArray();
					value.put("neighbors", ns);
					for (NeighboringCellInfo neighbor : neighbors) {
						JSONObject n =new JSONObject();
						if (neighbor.getCid()!=(-1))
							n.put("cid", neighbor.getCid());
						if (neighbor.getLac()!=(-1))
							n.put("lac", neighbor.getLac());
						n.put("networkType", neighbor.getNetworkType());
						n.put("rssi", neighbor.getRssi());
						ns.put(n);
					}
				}
			}
			catch (JSONException e) {
				// shouldn't
			}
			onNewValue(value);
		}
		return false;
	}

}
