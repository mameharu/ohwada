package jp.ohwada.android.pinqa1;

import java.util.List;

import jp.ohwada.android.pinqa1.task.ArticleList;
import jp.ohwada.android.pinqa1.task.ArticleListTask;
import jp.ohwada.android.pinqa1.task.ArticleRecord;
import jp.ohwada.android.pinqa1.task.GeocoderTask;
import jp.ohwada.android.pinqa1.task.ManageFile;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;

/*
 * Main Activity
 */
public class MainActivity extends MapActivity
	implements LocationListener {

	// debug
	private static final String TAG = Constant.TAG;
	private static final boolean D = Constant.DEBUG;
	private static final String TAG_SUB = "MainActivity";
	
	// gps
    private static final long LOCATION_MIN_TIME = 0L; 
    private static final float LOCATION_MIN_DISTANCE = 0f;
    private static final int DEFAULT_ACCURACY = 300;	// 300 m

	// marker
    private static final int DELAY_TIME = 100; 	// 0.1 sec
        
	// object
	private LocationManager mLocationManager;
   	private ArticleListTask mArticleTask;
   	private ManageFile mManageFile;
	private GeocoderTask mGeocoderTask = null;
	   	   	 	
	// view conponent
	private View mView;
	private MapView mMapView;
	private MapController mMapController;
	private MarkerItemizedOverlay mArticleOverlay;
    private GpsItemizedOverlay mGpsOverlay;
   	   	
	// task
	private boolean isArticleFinish = false;
		
	// variable
	private ArticleList mArticleList = null;
	private int mLat = 0;
	private int mLong = 0;
		
	/*
	 * === onCreate ===
	 * @param Bundle savedInstanceState
	 */
	@Override 
	public void onCreate( Bundle savedInstanceState ) {
    	super.onCreate( savedInstanceState );
    	mView = getLayoutInflater().inflate( R.layout.activity_main, null );
		setContentView( mView ); 
		
		// button option
		Button btnOption = (Button) findViewById( R.id.main_button_option );
		btnOption.setOnClickListener( new View.OnClickListener() {
			@Override
			public void onClick( View v) {
				showOptionDialog();
			}
		});

		// button get
		Button btnGet = (Button) findViewById( R.id.main_button_get );
		btnGet.setOnClickListener( new View.OnClickListener() {
			@Override
			public void onClick( View v) {
				getNewPoint();
			}
		});
		
    	// map
		mMapView = (MapView) findViewById( R.id.mapview );
    	mMapView.setClickable( true );
		mMapView.setBuiltInZoomControls( true ); 
		mMapController = mMapView.getController();

    	// marker
    	Drawable node_marker = getResources().getDrawable( R.drawable.marker_red );
    	mArticleOverlay = new MarkerItemizedOverlay( this, node_marker );
        mMapView.getOverlays().add( mArticleOverlay );
        
    	// gps
    	Drawable gps_marker = getResources().getDrawable( R.drawable.gps_blue_dot );
    	mGpsOverlay = new GpsItemizedOverlay( gps_marker );
    	mGpsOverlay.setDefaultAccuracy( DEFAULT_ACCURACY );
    	mMapView.getOverlays().add( mGpsOverlay );

     	// default
     	mLat = Constant.GEO_LAT;
		mLong = Constant.GEO_LONG;
		mMapController.setZoom( Constant.GEO_ZOOM );
		setCenter( new GeoPoint( mLat, mLong) ) ; 
		
		// location	   	
		mLocationManager = (LocationManager) getSystemService( LOCATION_SERVICE );
		
		// file initial
		mManageFile = new ManageFile();
		mManageFile.init();
		mManageFile.clearOldCache();

		// task		
		mArticleTask = new ArticleListTask( this, msgHandler );	
		mGeocoderTask = new GeocoderTask( this, mView, msgHandler );	
    	execTaskFromIntent( getIntent() );
	}

	/**
	 * === onNewIntent ===
	 */
    @Override
    public void onNewIntent( Intent intent ) {
    	execTaskFromIntent( intent );
    }

	/**
	 * execTaskFromIntent
	 * @param Intent intent
	 */
    private void execTaskFromIntent( Intent intent ) {
		// get geo point if from other activity
		if ( intent != null ) {
			UriGeo point = new UriGeo( intent );
			// set center if point is set
			if (( point != null )&& point.flag ) {
				mLat = doubleToE6( point.lat );
				mLong = doubleToE6( point.lng );
				setCenter( new GeoPoint( mLat, mLong ) ) ; 
			}
		}	
		// task		
		execTask();
	}
	    
    /**
	 * execTask
	 */  						
	private void execTask() {
		isArticleFinish = mArticleTask.execute( mLat, mLong );
		// if file exists
		if ( isArticleFinish ) {
			mArticleList = mArticleTask.getList();
    		// delayed execution, since showing markers takes time
			delayHandler.postDelayed( delayRunnable, DELAY_TIME ); 
		} 

    }
	
	/*
	 * === onResume ===
	 */
    @Override
    protected void onResume() {
        if ( mLocationManager != null ) {
        	// update GPS manager
            mLocationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 
                LOCATION_MIN_TIME,
                LOCATION_MIN_DISTANCE,
                this );
            // show Location  
			showGps( getLastLocation() );    
        } else {
        	// if NOT acquire GPS manager
        	Toast.makeText( this, R.string.gps_no_manager, Toast.LENGTH_SHORT ).show();
		}
        super.onResume();
    }

	/*
	 * === onPause ===
	 */
   @Override
    protected void onPause() {	
        if ( mLocationManager != null ) {
            mLocationManager.removeUpdates( this );
        }
        super.onPause();
    }

	/**
	 * === onDestroy ===
	 */
    @Override
    public void onDestroy() {
        super.onDestroy();
        mArticleTask.cancel();
        mGeocoderTask.cancel();
	}
	
	/*
	 * === isRouteDisplayed ===
	 * for MapAvtivity
	 * @return boolean
	 */
	@Override
	protected boolean isRouteDisplayed() {
		return false;
	}
	
	/*
	 * === onLocationChanged ===
	 * for LocationListener
	 * @param Location location
	 */
    @Override
    public void onLocationChanged( Location location ) {
     	showGps( location ) ;
    }
            
	/*
	 * === onProviderDisabled ===
	 * for LocationListener
	 * @param String provider
	 */
    @Override
    public void onProviderDisabled( String provider ) {
		// dummy
    }

	/*
	 * === onProviderEnabled ===
	 * for LocationListener
	 * @param String provider
	 */
    @Override
    public void onProviderEnabled( String provider ) {
		// dummy
    }

	/*
	 * === onStatusChanged ===
	 * for LocationListener
	 * @param String provider
	 * @param int status
	 * @param Bundle extras
	 */
	@Override
	public void onStatusChanged( String provider, int status, Bundle extras ) {
		// dummy	
	}
        
	/**
	 * === onActivityResult ===
	 * @param int request
	 * @param int result
	 * @param Intent data
	 */
	@Override
    public void onActivityResult( int request, int result, Intent data ) {
		super.onActivityResult( request, result, data );
    }

	/**
	 * === onCreateOptionsMenu ===
	 */
    @Override
    public boolean onCreateOptionsMenu( Menu menu ) {
		/* Initial of menu */
        MenuInflater inflater = getMenuInflater();
        inflater.inflate( R.menu.activity_main, menu );
        return super.onCreateOptionsMenu( menu );
    }
 
 	/**
	 * === onOptionsItemSelected ===
	 */
    @Override
    public boolean onOptionsItemSelected( MenuItem item ) {
		switch ( item.getItemId() ) {
			case R.id.menu_about:
				AboutDialog dialog = new AboutDialog( this );
				dialog.show();
				return true;
			case R.id.menu_clear:
				mManageFile.clearAllCache();
				return true;
        }
        return super.onOptionsItemSelected( item );
    }

// --- main ---
	/**
	 * setCenter
	 * @param GeoPoint point
     */
	private void setCenter( GeoPoint point ) {
		if ( point == null ) return; 
		mMapController.setCenter( point );
	}
	
    /**
     * showMarker
     * This process is slow, it takes about 7 seconds in Android 1.6 emulator
     */	
	private void showMarker() {
		// hide ProgressBar
//		mProgressDialog.hide();

		// if data is null				
		if ( mArticleList == null ) {
			toast_show( R.string.error_not_get_article );
			return;
		}
		
		List<ArticleRecord> list = mArticleList.getList();
		// no marker
		if (( list == null )||( list.size() == 0 )) {
			toast_show( R.string.error_not_get_article );
			return;
		}

		// show marker
		log_d( "showMarker start " + list.size() );	
		mArticleOverlay.clearPoints();
        for ( int i=0; i<list.size(); i++ ) {
			mArticleOverlay.addPoint( list.get( i ) );		
		}
		// redraw map, In order to show markers	
		mMapView.invalidate();  
		log_d( "showMarker end" );
	}

	/**
	 * getNewPoint
     */
	private void getNewPoint() {
		GeoPoint point = mMapView.getMapCenter();
		mLat = point.getLatitudeE6();
		mLong = point.getLongitudeE6();
		execTask();
	}			

// --- GPS ---
	/**
	 * get Last Location. 
	 * @return Location
	 * <pre>
	 * This is cache value, NOT present location. 
	 * when get a location at A point, and NOT get a location at B point, value is a location of A point.
	 * </pre>
     */
    private Location getLastLocation() {
		if ( mLocationManager == null ) return null;
    	return mLocationManager.getLastKnownLocation( LocationManager.GPS_PROVIDER );
	}

	/**
	 * showGps 
	 * @param Location
     */
    private void showGps( Location location ) {
    	if ( location == null ) return ;
		mGpsOverlay.setLocation( location ); 
		// redraw map, In order to show marker	
		mMapView.invalidate();  
    }
    
    /**
	 * moveGps 
     */
    private void moveGps() {
    	Location location = getLastLocation();
    	if ( location == null ) {
			toast_show( R.string.gps_not_found );
    		return ;
    	}	
    	GeoPoint point = new GeoPoint( 
			doubleToE6( location.getLatitude() ), 
			doubleToE6( location.getLongitude() ) );
		setCenter( point );
		showGps( location );
    }

	/**
	 * convert real number to integer
	 * @param Double : location( floating point format )
	 * @return int : location( E6 format )
	 */
    private int doubleToE6( Double d1 ) {
		Double d2 = d1 * 1E6;
		return d2.intValue();
	}
// --- GPS end ---
				
// --- Search ---
	/**
	 * searchLocation
	 * @param String location
	 */
	private void serachLocation( String location ) {
		// nothig if no input
		if ( location.length() == 0 ) return;
		if ( location.equals("") ) return;
		
		// search location
//		hideInputMethod();
//		mProgressDialog.show( 
//			Constant.MSG_ARG1_DIALOG_PROGRESS_SEARCHING, 
//			R.string.searching );	

//		mGeocoderTask.setLocation( location );
		mGeocoderTask.execute( location );
	}

	private void showLocation( List<Address> list ) {					
//		List<Address> list = mGeocoder.getAddressListRetry( location );
//		mProgressDialog.hide(); 
				
		// if NOT found
		if (( list == null ) || list.isEmpty() ) {
			toast_show( R.string.search_not_found );
			return;
		}
											 	
		// get point
		Address addr = list.get( 0 );
		int lat = doubleToE6( addr.getLatitude() ); 
		int lng = doubleToE6( addr.getLongitude() );
		GeoPoint point = new GeoPoint( lat, lng );

		// set center of map
		setCenter( point );
		mMapController.setZoom( Constant.GEO_ZOOM );

		toast_show( R.string.search_found );
	}
	
	/**
	 * hide software keyboard
	 * @param View view
	 */
//	private void hideInputMethod() {
//        mInputManager.hideSoftInputFromWindow( mView.getWindowToken(), InputMethodManager.HIDE_IMPLICIT_ONLY ); 
//	}
// --- Search end ---

// --- Menu Dialog ---
	private void showOptionDialog() {
		OptionDialog dialog = new OptionDialog( this );
		dialog.setHandler( msgHandler, Constant.MSG_WHAT_DIALOG_OPTION );
		dialog.create();
		dialog.show();
	}
// --- Dialog end ---
        					
// --- Handler class ----
	private final Handler delayHandler = new Handler();

// --- Runnable class ----    
	private final Runnable delayRunnable = new Runnable() {
		@Override
		public void run() {
			showMarker();
    	}
	};

// --- Message Handler ---
	/**
	 * Message Handler
	 */
	private Handler msgHandler = new Handler() {
        @Override
        public void handleMessage( Message msg ) {
			execHandler( msg );
        }
    };

	/**
	 * execHandler
	 * @param Message msg
	 */
	private void execHandler( Message msg ) {
		    switch ( msg.what ) {
            case Constant.MSG_WHAT_DIALOG_OPTION:
            	execHandlerMove( msg );
                break;
            case Constant.MSG_WHAT_TASK_ARTICLE_LIST:
            	isArticleFinish = true;
                mArticleList= mArticleTask.getList();
    			showMarker();
                break;                
//            case Constant.MSG_WHAT_DIALOG_PROGRESS:
//            	cancelTask();
//                break; 
            case Constant.MSG_WHAT_TASK_GEOCODER:
            	showLocation( mGeocoderTask.getList() );
                break; 
        }
	}

	/**
	 * execHandlerMove
	 * @param Message msg
	 */
	private void execHandlerMove( Message msg ) {
    	switch ( msg.arg1 ) {
            case Constant.MSG_ARG1_DIALOG_OPTION_DEFAULT:
				setCenter( new GeoPoint( Constant.GEO_LAT, Constant.GEO_LONG ) );
                break;
            case Constant.MSG_ARG1_DIALOG_OPTION_GPS:
				moveGps();
                break;
            case Constant.MSG_ARG1_DIALOG_OPTION_SEARCH:
            	String location = msg.getData().getString( Constant.BUNDLE_DIALOG_OPTION_LOCATION );
				serachLocation( location );
                break;
        }
	}

	/**
	 * toast_show
	 * @param int id
	 */ 
	private void toast_show( int id ) {
		Toast.makeText( this, id, Toast.LENGTH_SHORT ).show();
	}
		
	/**
	 * write log
	 * @param String msg
	 */ 
	private void log_d( String msg ) {
	    if (D) Log.d( TAG, TAG_SUB + " " + msg );
	}
}
