/**
 * 
 */
package uk.ac.horizon.ubihelper.ui;

import uk.ac.horizon.ubihelper.R;
import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

/**
 * @author cmg
 *
 */
public class AboutActivity extends Activity {

	/* (non-Javadoc)
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */
	private static final String TAG = "ubihelper-about";
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.about);
		TextView version = (TextView)findViewById(R.id.about_version_text);
		version.setText(getVersion());
	}
    /** 
     * Get current version number. 
     * 
     * @return 
     */ 
    private String getVersion() { 
            String version = "?"; 
            try { 
                    PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0); 
                    version = pi.versionName+" ("+pi.versionCode+")";
            } catch (PackageManager.NameNotFoundException e) { 
                    Log.e(TAG, "Package name not found", e); 
            }; 
            return version; 
    } 
}
