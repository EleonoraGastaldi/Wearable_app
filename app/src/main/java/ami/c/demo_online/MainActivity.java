package ami.c.demo_online;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
//import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessOptions;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSource;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.data.Value;
import com.google.android.gms.fitness.request.DataSourcesRequest;
import com.google.android.gms.fitness.request.OnDataPointListener;
import com.google.android.gms.fitness.request.SensorRequest;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import common.logger.Log;
import common.logger.LogView;
import common.logger.LogWrapper;
import common.logger.MessageOnlyLogFilter;

import static java.util.Objects.requireNonNull;

public class MainActivity extends AppCompatActivity {

    public static final String TAG = "IWatch";
    private GoogleApiClient mClient = null;

    private static final int GOOGLE_FIT_PERMISSIONS_REQUEST_CODE = 0533;
    private static final int REQUEST_OAUTH_REQUEST_CODE = 1;
    private static final int REQUEST_PERMISSIONS_REQUEST_CODE = 34;

    // [START mListener_variable_reference]
    //Dichiaro un listener, che utilizzerò per le funzioni di registrazione e dis-registrazione (non vorrò più inviare dati a tale listener)
    private OnDataPointListener mListener;
    // [END mListener_variable_reference]

    // [START auth_oncreate_setup]
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Put application specific code here -> applicationId "ami.c.demo_online"

        setContentView(R.layout.activity_main);
        // Fa il set up del logger utente: stampa sul video dello smartwatch gli stessi log msg che appaiono in logcat

        initializeLogging();

        //Quando i permessi vengono annullati, l'app esegure restart -> onCreate è sufficiente per verificare i permessi fondamentali dell'Activity
        if (hasRuntimePermissions()) {
            Log.i(TAG, "ho i permessi runTime");
            findFitnessDataSourcesWrapper();
        } else {
            requestRuntimePermissions();
        }
    }

    // OAuth è un protocollo di autenticazione standard che permette all'utente di approvare l'interconnessione tra due app
    // senza che la seconda conosca dati personali (come la password) -> utilizza token di autorizzazione che permettono la comunicazione tra consumers e service providers
    // Wrapper per {@link #findFitnessDataSources}. Se l'utente ha permesso OAuth,
    // continua con {@link #findFitnessDataSources}, altrimenti richiedilo.

    private void findFitnessDataSourcesWrapper() {
        if (hasOAuthPermission()) {
            Log.i(TAG, "Ho OAuth permissions");
            findFitnessDataSources();
        } else {
            requestOAuthPermission();
        }
    }

    // Ottiene {@link FitnessOptions} per verificare o richiedere OAuth permission per l'utente.
    private FitnessOptions getFitnessSignInOptions() {
        return FitnessOptions.builder().addDataType(DataType.TYPE_HEART_RATE_BPM, FitnessOptions.ACCESS_READ).build();
    }

    // Verifica se l'account dell'utente ha l'OAuth permission per le Fitness API.
    private boolean hasOAuthPermission() {
        FitnessOptions fitnessOptions = getFitnessSignInOptions();
        return GoogleSignIn.hasPermissions(GoogleSignIn.getLastSignedInAccount(this), fitnessOptions);
    }

    // Avvia il metodo Google SignIn per richiedere l'OAuth permission per l'utente
    private void requestOAuthPermission() {
        FitnessOptions fitnessOptions = getFitnessSignInOptions();
        GoogleSignIn.requestPermissions(
                this,
                REQUEST_OAUTH_REQUEST_CODE,
                GoogleSignIn.getLastSignedInAccount(this),
                fitnessOptions);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Questo metodo assicura che se l'utente nega i permessi, poi usa Settings per riabilitarli, l'app funzionerà nuovamente
        findFitnessDataSourcesWrapper();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQUEST_OAUTH_REQUEST_CODE) {
                findFitnessDataSources();
            }
        }
    }
    // [END auth_oncreate_setup]

    // Trova i dati sorgente disponibili e prova a registrarli in un specifico {@link DataType}.
    private void findFitnessDataSources() {
        mClient = new GoogleApiClient.Builder(this)
                .addScope(new Scope(Scopes.FITNESS_LOCATION_READ))
                .addScope(new Scope(Scopes.FITNESS_BODY_READ))
                .addScope(new Scope(Scopes.FITNESS_ACTIVITY_READ))
                .addApiIfAvailable(Fitness.RECORDING_API)
                .addApiIfAvailable(Fitness.HISTORY_API)
                .addApiIfAvailable(Fitness.SENSORS_API)
                .addScope(new Scope(Scopes.FITNESS_ACTIVITY_READ_WRITE))
                .addConnectionCallbacks(
                        new GoogleApiClient.ConnectionCallbacks() {
                            @Override
                            public void onConnected(Bundle bundle) {
                                Log.i(TAG, "Connected!!!");
                            }
                            @Override
                            public void onConnectionSuspended(int i) {
                                if (i == GoogleApiClient.ConnectionCallbacks.CAUSE_NETWORK_LOST) {
                                } else if (i
                                        == GoogleApiClient.ConnectionCallbacks.CAUSE_SERVICE_DISCONNECTED) {
                                }
                            }
                        }
                )
                .build();

        Log.i(TAG, "try to execute findFitnessDataSources");
        // [START find_data_sources]

        DataSourcesRequest dtSourceREq = new DataSourcesRequest.Builder()
                .setDataTypes(DataType.TYPE_HEART_RATE_BPM)
                .setDataSourceTypes(DataSource.TYPE_RAW)
                .build();
        Log.i(TAG, "Ho riempito dtSourceReq");
        Log.i(TAG, "Trovato: "+ dtSourceREq.getDataTypes().toString());

        //Fitness.getSensorsClient(this, requireNonNull(GoogleSignIn.getLastSignedInAccount(this)))
        Fitness.getSensorsClient(this, GoogleSignIn.getLastSignedInAccount(this.getApplicationContext()))
                  .findDataSources(dtSourceREq)
                .addOnSuccessListener(this,
                        new OnSuccessListener<List<DataSource>>() {
                            @Override
                            public void onSuccess(List<DataSource> dtSourceREq) {
                                if(dtSourceREq.isEmpty())
                                    Log.i(TAG, "Non sono presenti elementi nella lista");
                                for (DataSource dataSource : dtSourceREq) {
                                    Log.i(TAG, "Data source found: " + dataSource.toString());
                                    Log.i(TAG, "Data Source type: " + dataSource.getDataType().getName());

                                    // Registra un listener in modo che questo possa ricevere i dati dell'Activity
                                    if (dataSource.getDataType().equals(DataType.TYPE_HEART_RATE_BPM)
                                            && mListener == null) {
                                        Log.i(TAG, "Data source for HEART_RATE_BPM found!  Registering.");
                                        registerFitnessDataListener(dataSource, DataType.TYPE_HEART_RATE_BPM);
                                    }
                                }
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Log.e(TAG, "Exception: failureListener", e);
                            }
                        });
        // [END find_data_sources]
    }

     // Il metodo registra un listener con le Sensors API per il {@link DataSource} fornito e per {@linkDataType}
    private void registerFitnessDataListener(DataSource dataSource, DataType dataType) {
        Log.i(TAG, "try to execute registerFitnessDataListener");
        // [START register_data_listener]
        mListener =
                new OnDataPointListener() {
                    @Override
                    public void onDataPoint(DataPoint dataPoint) {
                        Log.i(TAG, "onDataPonitListener");
                        for (Field field : dataPoint.getDataType().getFields()) {
                            Value val = dataPoint.getValue(field);
                            Log.i(TAG, "Detected DataPoint field: " + field.getName());
                            Log.i(TAG, "Detected DataPoint value: " + val);
                        }
                    }
                };
        Log.i(TAG, "mListener ready");

        //Fitness.getSensorsClient(this, GoogleSignIn.getLastSignedInAccount(this))
        Fitness.getSensorsClient(this, requireNonNull(GoogleSignIn.getLastSignedInAccount(this)))
                .add(
                        new SensorRequest.Builder()
                                .setDataSource(dataSource) // Utile per l'utente data sets
                                .setDataType(dataType)
                                .setSamplingRate(10, TimeUnit.SECONDS)
                                .build(),
                        mListener)
                .addOnCompleteListener(
                        new OnCompleteListener<Void>() {
                            @Override
                            public void onComplete(@NonNull Task<Void> task) {
                                if (task.isSuccessful()) {
                                    Log.i(TAG, "Listener registered!");
                                } else {
                                    Log.e(TAG, "Listener not registered.", task.getException());
                                }
                            }
                        });
        // [END register_data_listener]
    }

    // Dis-registrazione del listener con le Sensors API
    private void unregisterFitnessDataListener() {
        if (mListener == null) {
            // Uso 1 solo listener alla volta -> se non c'è un listener, non devo effettuare registrazioni
            return;
        }

        // [START unregister_data_listener]
        // Waiting isn't actually necessary as the unregister call will complete regardless,
        // even if called from within onStop, but a callback can still be added in order to
        // inspect the results.
        //Fitness.getSensorsClient(this, GoogleSignIn.getLastSignedInAccount(this))
        Fitness.getSensorsClient(this, requireNonNull(GoogleSignIn.getLastSignedInAccount(this)))
                .remove(mListener)
                .addOnCompleteListener(
                        new OnCompleteListener<Boolean>() {
                            @Override
                            public void onComplete(@NonNull Task<Boolean> task) {
                                //if (task.isSuccessful() && task.getResult()) {
                                if (task.isSuccessful() && requireNonNull(task).getResult()) {
                                    Log.i(TAG, "Listener was removed!");
                                } else {
                                    Log.i(TAG, "Listener was not removed.");
                                }
                            }
                        });
        // [END unregister_data_listener]
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Aggiunge items all'action bar (se presente)
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_unregister_listener) {
            unregisterFitnessDataListener();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Inizializza una custom log class i cui outputs saranno visualizzati in-app targets e nel logcat
    private void initializeLogging() {
        // Wraps Android's native log framework.
        LogWrapper logWrapper = new LogWrapper();
        // Using Log, front-end to the logging chain, emulates android.util.log method signatures.
        Log.setLogNode(logWrapper);
        // Filter strips out everything except the message text.
        MessageOnlyLogFilter msgFilter = new MessageOnlyLogFilter();
        logWrapper.setNext(msgFilter);
        // On screen logging via a customized TextView.
        LogView logView = (LogView) findViewById(R.id.sample_logview);

        // Fixing this lint errors adds logic without benefit.
        // noinspection AndroidLintDeprecation
        //logView.setTextAppearance(R.style.Log);

        logView.setBackgroundColor(Color.WHITE);
        msgFilter.setNext(logView);
        Log.i(TAG, "Ready: inizializeLogging_success");
    }

    // Ritorna lo stato attuale dei permessi necessari
    private boolean hasRuntimePermissions() {
        int permissionState =
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
        return permissionState == PackageManager.PERMISSION_GRANTED;
    }

    private void requestRuntimePermissions() {
        boolean shouldProvideRationale =
                ActivityCompat.shouldShowRequestPermissionRationale(
                        this, Manifest.permission.ACCESS_FINE_LOCATION);

        // Fornisce una logica ulteriore: se l'utente nega la richiesta prima di aver cliccato "Don't ask again"
        if (shouldProvideRationale) {
            Log.i(TAG, "Displaying permission rationale to provide additional context.");
            Snackbar.make(
                    findViewById(R.id.main_activity_view),
                    R.string.permission_rationale,
                    Snackbar.LENGTH_INDEFINITE)
                    .setAction(
                            R.string.ok,
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    // Request permission
                                    ActivityCompat.requestPermissions(
                                            MainActivity.this,
                                            new String[] {Manifest.permission.ACCESS_FINE_LOCATION},
                                            REQUEST_PERMISSIONS_REQUEST_CODE);
                                }
                            })
                    .show();
        } else {
            Log.i(TAG, "Requesting permission");
            // Request permission. It's possible this can be auto answered if device policy
            // sets the permission in a given state or the user denied the permission
            // previously and checked "Never ask again".
            ActivityCompat.requestPermissions(
                    MainActivity.this,
                    new String[] {Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_PERMISSIONS_REQUEST_CODE);
        }
    }

    // Callback ricevuta quando la permissions request è stata completata
    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Log.i(TAG, "onRequestPermissionResult");
        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {
            if (grantResults.length <= 0) {
                // If user interaction was interrupted, the permission request is cancelled and you
                // receive empty arrays.
                Log.i(TAG, "User interaction was cancelled.");
            } else if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission was granted.
                findFitnessDataSourcesWrapper();
            } else {
                // Permission denied.

                // This Activity notifies the user that he has rejected a core permission for the app since it makes the Activity useless.

                // Additionally, it is important to remember that a permission might have been
                // rejected without asking the user for permission (device policy or "Never ask
                // again" prompts). Therefore, a user interface affordance is typically implemented
                // when permissions are denied. Otherwise, your app could appear unresponsive to
                // touches or interactions which have required permissions.
                Snackbar.make(
                        findViewById(R.id.main_activity_view),
                        R.string.permission_denied_explanation,
                        Snackbar.LENGTH_INDEFINITE)
                        .setAction(
                                R.string.settings,
                                new View.OnClickListener() {
                                    @Override
                                    public void onClick(View view) {
                                        // Build intent that displays the App settings screen.
                                        Intent intent = new Intent();
                                        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                        Uri uri = Uri.fromParts("package", BuildConfig.APPLICATION_ID, null);
                                        intent.setData(uri);
                                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                        startActivity(intent);
                                    }
                                })
                        .show();
            }
        }
    }
}