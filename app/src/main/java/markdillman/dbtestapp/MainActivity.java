package markdillman.dbtestapp;

import android.app.Activity;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v4.app.ActivityCompat;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.EditText;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;


public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener{

    public class CoordinateSet {

        public double latitude;
        public double longitude;

        public CoordinateSet(){
            //default coordinates to OSU
            latitude = 44.5;
            longitude = -123.2;
        }
    }

    private GoogleApiClient mGoogleApiClient;
    public LocationRequest mLocationRequest;
    private SQLiteExample mSQLiteExample;
    private SQLiteDatabase mSQLDB;
    private Button mButton;
    private Cursor mSQLCursor;
    private SimpleCursorAdapter mSQLCursorAdapter;
    private ListView mSQLList;
    private Location mLastLocation;
    private Location curLocation;
    private LocationListener mLocationListener;
    private TextView mLatText;
    private TextView mLonText;
    private static final int LOCATION_PERMISSION_RESULT = 24;
    private CoordinateSet currentCoords;
    private CoordinateSet defaultCoords;
    private String TAG = "NavDB";

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (mGoogleApiClient == null){
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }

        mLatText = (TextView)findViewById(R.id.templat);
        mLonText = (TextView)findViewById(R.id.templon);
        currentCoords = new CoordinateSet();
        defaultCoords = new CoordinateSet();

        mLocationRequest = LocationRequest.create();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setInterval(5000);
        mLocationRequest.setFastestInterval(5000);

        mLocationListener = new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    if (location != null) {
                        setCoordinatePair(currentCoords,location);
                    }
                }
        };


        //DATABASE STUFF
        mSQLiteExample = new SQLiteExample(this);
        mSQLDB = mSQLiteExample.getWritableDatabase();

        //Button
        mButton = (Button) findViewById(R.id.submit_button);
        mButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                //if permits are valid, enter last known location
                if (validPerms() && mLastLocation != null){
                    //if current coordinates are still stuck on no-permit, update to last location
                    if (currentCoords.latitude==defaultCoords.latitude && currentCoords.longitude==defaultCoords.longitude) {
                        setCoordinatePair(currentCoords, mLastLocation);
                    }
                }
                //else return to default
                else {
                    setCoordinatePair(currentCoords,null);
                }
                if (mSQLDB != null){
                    ContentValues vals = new ContentValues();
                    vals.put(DBContract.DemoTable.COLUMN_NAME_DEMO_STRING, ((EditText)findViewById(R.id.user_string)).getText().toString());
                    vals.put(DBContract.DemoTable.COLUMN_NAME_DEMO_LAT,currentCoords.latitude);
                    vals.put(DBContract.DemoTable.COLUMN_NAME_DEMO_LON,currentCoords.longitude);
                    mSQLDB.insert(DBContract.DemoTable.TABLE_NAME,null,vals);
                    populateList();
                }
            }
        });
        populateList();
    }

    private void populateList(){
        if(mSQLDB!=null){
            try {
                if(mSQLCursorAdapter!=null && mSQLCursorAdapter.getCursor()!=null){
                    if(!mSQLCursorAdapter.getCursor().isClosed()){
                        mSQLCursorAdapter.getCursor().close();
                    }
                }
                mSQLCursor = mSQLDB.rawQuery("select * from demo",null);
                mSQLList = (ListView) findViewById(R.id.sql_list);
                mSQLCursorAdapter = new SimpleCursorAdapter(this,R.layout.list_element,
                        mSQLCursor,new String[]{DBContract.DemoTable.COLUMN_NAME_DEMO_STRING,
                        DBContract.DemoTable.COLUMN_NAME_DEMO_LAT,DBContract.DemoTable.COLUMN_NAME_DEMO_LON},
                        new int[]{R.id.elemstring,R.id.elemlat,R.id.elemlon},0);
                mSQLList.setAdapter(mSQLCursorAdapter);

            } catch (Exception e) {
                Log.d(TAG, "Error loading data from database.");
            }
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle){
        if (ActivityCompat.checkSelfPermission(this,android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(this, new String[] {android.Manifest.permission.ACCESS_COARSE_LOCATION,
                    android.Manifest.permission.ACCESS_FINE_LOCATION},LOCATION_PERMISSION_RESULT);
        }
        if (ActivityCompat.checkSelfPermission(this,android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            //if permissions denied or revoked, set coordinates (last known) to OSU
            //setDefaultCoords(mLastLocation);
        }
        else {
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient,mLocationRequest,mLocationListener);
            mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
            getCoordinates();
        }
    }

    @Override
    protected void onStart() {
        mGoogleApiClient.connect();
        super.onStart();
    }

    @Override
    protected void onStop() {
        mGoogleApiClient.disconnect();
        super.onStop();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == LOCATION_PERMISSION_RESULT) {
            if (validPerms()){
                LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient,mLocationRequest,mLocationListener);
                mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
                getCoordinates();
            }
        }
    }

    private boolean validPerms(){
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            //if permissions denied or revoked, set coordinates (last known) to OSU
            return true;
        }
        else return false;
    }

    @Override
    public void onConnectionFailed(@Nullable ConnectionResult connectionResult){
        Dialog connectionError = GoogleApiAvailability.getInstance().getErrorDialog(this, connectionResult.getErrorCode(),0);
        connectionError.show();
    }

    @Override
    public void onConnectionSuspended(int cause){

    }

    private void getCoordinates(){
        if (!validPerms()){
            mLonText.setText("Invalid perms or bad location");
        }
        else {
                //mLonText.setText(String.valueOf(mLastLocation.getLongitude()));
                //mLatText.setText(String.valueOf(mLastLocation.getLatitude()));
        }
    }

    private void setCoordinatePair(CoordinateSet coords, @Nullable Location loc){
        if (!validPerms()){
            coords.latitude = 44.5;
            coords.longitude = -123.2;
        }
        else if (loc != null) {
            coords.latitude = loc.getLatitude();
            coords.longitude = loc.getLongitude();
        }
        else {
            coords.latitude = 44.5;
            coords.longitude = -123.2;
        }
    }

    final class DBContract {

        private DBContract(){};

        public final class DemoTable implements BaseColumns{
            public static final String DB_NAME = "demo_db";
            public static final String TABLE_NAME = "demo";
            public static final String COLUMN_NAME_DEMO_STRING = "demo_string";
            public static final String COLUMN_NAME_DEMO_LAT = "demo_lat";
            public static final String COLUMN_NAME_DEMO_LON = "demo_lon";
            public static final int DB_VERSION = 4;

            public static final String SQL_CREATE_DEMO_TABLE = "CREATE TABLE " +
                    DemoTable.TABLE_NAME + "(" + DemoTable._ID + " INTEGER PRIMARY KEY NOT NULL," +
                    DemoTable.COLUMN_NAME_DEMO_STRING + " VARCHAR(255)," +
                    DemoTable.COLUMN_NAME_DEMO_LAT + " DOUBLE," +
                    DemoTable.COLUMN_NAME_DEMO_LON + " DOUBLE);";

            public static final String SQL_DROP_DEMO_TABLE = "DROP TABLE IF EXISTS " + DemoTable.TABLE_NAME;
        }
    }

    class SQLiteExample extends SQLiteOpenHelper {

        public SQLiteExample(Context context){
            super(context, DBContract.DemoTable.DB_NAME,null,DBContract.DemoTable.DB_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db){
            db.execSQL(DBContract.DemoTable.SQL_CREATE_DEMO_TABLE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion){
            db.execSQL(DBContract.DemoTable.SQL_DROP_DEMO_TABLE);
            onCreate(db);
        }
    }
}
