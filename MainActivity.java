package uk.co.scribbleapps.customsoundboardmaker;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;

enum VerifyName {TOO_LONG, DUPLICATE, EMPTY, OK}

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivityTAG";

    private SharedPreferences prefs;
    private SharedPreferences.Editor prefsEditor;
    private final String KEY_CURRENT_SOUNDBOARD = "keyCurrentSoundboard";
    private final String KEY_SOUNDBOARDS = "keySoundboards";
    public final String SHARED_PREFERENCES = "sharedPrefs";

    private AlertDialog alertDialog;
    private ArrayList<MainActivitySoundboardObject> soundboardNamesList;
    private Boolean soundboardNameverified;
    private Boolean onCreateAlreadyRun;
    private MainActivitySoundboardAdapter mAdapter;
    private String currentSoundboardName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Assign some variables and inflate toolbar
        Toolbar myToolbar = findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);
        myToolbar.inflateMenu(R.menu.menu);
        prefs = getSharedPreferences(SHARED_PREFERENCES, MODE_PRIVATE);
        prefsEditor = prefs.edit();
        onCreateAlreadyRun = false;

        // Check network connection
        ConnectivityManager cm = (ConnectivityManager) MainActivity.this.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();

        // No network connection
        if (!isConnected) {
            startActivity(new Intent(MainActivity.this, NoNetworkActivity.class));

            // Network connection found
        } else {
            soundboardNamesList = new ArrayList<>();
            soundboardNamesList = getSoundboardsFromSharedPrefs();

            // Show a dialog with some details about the app - only on the 1st opening of the app
            String KEY_FIRST_OPEN = "firstOpen";
            if (prefs.getBoolean(KEY_FIRST_OPEN, true)) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("About")
                        .setMessage(R.string.about_full_text)
                        .setPositiveButton(R.string.close, null)
                        .create().show();
                prefsEditor.putBoolean(KEY_FIRST_OPEN, false).apply();
            }

            // Button to add a soundboard
            ImageView imageViewAddSoundboard = findViewById(R.id.imageViewAddSoundboard);
            imageViewAddSoundboard.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    addSoundDialog();
                }
            });

            // Set up the RecyclerView for the list of soundboards and set the OnClickListeners
            buildRecyclerView();
        }
    }

    // Drop down menu - top right of the screen
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu, this adds items to the action bar if it is present
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    // Drop down menu - top right of the screen
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            // Send an email
            case R.id.action_contact:
                Intent sendEmail = new Intent(android.content.Intent.ACTION_SEND);
                sendEmail.setType("plain/text");
                sendEmail.putExtra(android.content.Intent.EXTRA_EMAIL, new String[]{"tom@scribbleapps.co.uk"});
                sendEmail.putExtra(android.content.Intent.EXTRA_SUBJECT, "Soundboard Creator enquiry");
                startActivity(Intent.createChooser(sendEmail, "Send email..."));
                return true;

            // Display some information about the app
            case R.id.action_about:
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("About")
                        .setMessage(R.string.about_full_text)
                        .setPositiveButton(R.string.close, null)
                        .create().show();
                return true;

            // Show a dialog displaying the URL for the Scribble Apps Privacy Policy
            case R.id.action_privacy:
                //Linkify the message
                final SpannableString s = new SpannableString(getResources().getString(R.string.privacy_full_text)); // msg should have url to enable clicking
                Linkify.addLinks(s, Linkify.WEB_URLS);
                final AlertDialog privacyAlertDialog = new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Privacy")
                        .setMessage(s)
                        .setPositiveButton(R.string.close, null)
                        .create();
                privacyAlertDialog.show();
                ((TextView) privacyAlertDialog.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
                return true;

            // If we got here, the user's action was not recognized
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    // Called when a user clicks the plus symbol to add a new soundboard
    private void addSoundDialog() {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(MainActivity.this);
        LayoutInflater inflater = getLayoutInflater();
        final View dialogView = inflater.inflate(R.layout.custom_dialog_add_soundboard, null);
        dialogBuilder.setView(dialogView);

        TextView cancel = dialogView.findViewById(R.id.textViewCancel);
        TextView confirm = dialogView.findViewById(R.id.textViewConfirm);
        final EditText userinput = dialogView.findViewById(R.id.editTextUserInput);

        alertDialog = dialogBuilder.create();
        alertDialog.show();

        // This variable ensures we only create a new soundboard name if it passes all checks below
        soundboardNameverified = true;

        // Check for empty strings, duplicates and names that are too long before creating the new soundboard name
        confirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // Get the name of the new soundboard from the user input
                final String newSBName = userinput.getText().toString();

                // Only add the new soundboard name if it passes all checks
                if (verifySoundboardName(newSBName) == VerifyName.OK) {
                    insertItem(soundboardNamesList.size(), newSBName);
                    alertDialog.dismiss();
                }
            }
        });

        // User has chosen not to continue with adding a soundboard
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                alertDialog.dismiss();
            }
        });
    }

    public void buildRecyclerView() {
        RecyclerView recyclerView = findViewById(R.id.recyclerViewMain);
        recyclerView.setHasFixedSize(true);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        mAdapter = new MainActivitySoundboardAdapter(soundboardNamesList);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(mAdapter);

        // Normal click - open SoundboardActivity
        // Long click - give the option to delete the soundboard
        mAdapter.setOnItemClickListener(new MainActivitySoundboardAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(int position) {
                currentSoundboardName = ((MainActivitySoundboardObject) soundboardNamesList.get(position)).getmSoundboardName();
                startActivity(new Intent(MainActivity.this, SoundboardActivity.class).putExtra(KEY_CURRENT_SOUNDBOARD, currentSoundboardName));
            }

            public boolean onLongItemClick(final int position) {
                currentSoundboardName = ((MainActivitySoundboardObject) soundboardNamesList.get(position)).getmSoundboardName();
                // Dialog giving option to delete the soundboard or cancel
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(currentSoundboardName)
                        .setMessage("Delete this soundboard?")
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.confirm, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                removeItem(position, currentSoundboardName);
                            }
                        }).create().show();
                return true;
            }

            @Override
            public void onEditClick(final int position) {
                currentSoundboardName = ((MainActivitySoundboardObject) soundboardNamesList.get(position)).getmSoundboardName();


                AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(MainActivity.this);
                LayoutInflater inflater = getLayoutInflater();
                final View dialogView = inflater.inflate(R.layout.custom_dialog_edit_soundboard, null);
                dialogBuilder.setView(dialogView);

                TextView title = dialogView.findViewById(R.id.textViewSoundboardNameEdit);
                title.setText(currentSoundboardName);
                TextView cancel = dialogView.findViewById(R.id.textViewCancel);
                TextView confirm = dialogView.findViewById(R.id.textViewConfirm);
                final EditText userinput = dialogView.findViewById(R.id.editTextUserInput);

                alertDialog = dialogBuilder.create();
                alertDialog.show();

                confirm.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        final String newSBName = userinput.getText().toString();

                        // Only edit the soundboard name if the new name passes all checks
                        if (verifySoundboardName(newSBName) == VerifyName.OK) {
                            editItem(position, currentSoundboardName, newSBName);
                            alertDialog.dismiss();
                        }
                    }
                });

                cancel.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        alertDialog.dismiss();
                    }
                });
            }
        });
    }

    private VerifyName verifySoundboardName(String soundboardName) {
        // Empty String check
        if (soundboardName.equals("")) {
            Toast.makeText(MainActivity.this, "Please enter a name", Toast.LENGTH_SHORT).show();
            return VerifyName.EMPTY;
        }
        // Duplicate check
        for (Object sbObject : soundboardNamesList) {
            MainActivitySoundboardObject tempObject = (MainActivitySoundboardObject) sbObject;
            if (null != tempObject && tempObject.getmSoundboardName().equals(soundboardName)) {
                Toast.makeText(MainActivity.this, "This name already exists, please try another", Toast.LENGTH_SHORT).show();
                return VerifyName.DUPLICATE;
            }
        }
        // Length check
        if (soundboardName.length() > 20) {
            Toast.makeText(MainActivity.this, "This name is too long, please keep to under 20 characters", Toast.LENGTH_SHORT).show();
            return VerifyName.TOO_LONG;
        }
        // Every test has been passed, return OK
        return VerifyName.OK;
    }

    public void insertItem(int position, String soundboardName) {
        soundboardNamesList.add(new MainActivitySoundboardObject(soundboardName, R.drawable.pen2));
        mAdapter.notifyItemInserted(position);
        setSoundboardsToSharedPrefs(soundboardNamesList);
        rebuildRecyclerView();
    }

    public void removeItem(int position, String soundboardName) {
        SQLiteDB sqLiteDB = new SQLiteDB(MainActivity.this);
        try {
            sqLiteDB.removeSoundboard(soundboardName);
        } catch (Exception e) {
            Log.e(TAG, "editItem: error: " + e.getMessage());
        }
        soundboardNamesList.remove(position);
        mAdapter.notifyItemRemoved(position);
        setSoundboardsToSharedPrefs(soundboardNamesList);
        rebuildRecyclerView();
    }

    public void editItem(int position, String existingSoundboardName, String newSoundboardName) {
        SQLiteDB dbHelper = new SQLiteDB(MainActivity.this);
        try {
            dbHelper.amendSoundboardObjects(existingSoundboardName, newSoundboardName);
        } catch (Exception e) {
            Log.e(TAG, "editItem: error: " + e.getMessage());
        }
        soundboardNamesList.get(position).setmSoundboardName(newSoundboardName);
        mAdapter.notifyItemChanged(position);
        setSoundboardsToSharedPrefs(soundboardNamesList);
        rebuildRecyclerView();
    }

    private void setSoundboardsToSharedPrefs(ArrayList<MainActivitySoundboardObject> list) {
        // Convert the MainActivitySoundboardObject to json before storing in Shared Prefs
        Gson gson = new Gson();
        String json = gson.toJson(list);
        prefsEditor.putString(KEY_SOUNDBOARDS, json);
        prefsEditor.apply();
    }

    private ArrayList<MainActivitySoundboardObject> getSoundboardsFromSharedPrefs() {
        // Convert from json to MainActivitySoundboardObject
        if (prefs.getString(KEY_SOUNDBOARDS, null) != null) {
            Gson gson = new Gson();
            ArrayList<MainActivitySoundboardObject> soundboardList;
            String string = prefs.getString(KEY_SOUNDBOARDS, null);
            Type type = new TypeToken<ArrayList<MainActivitySoundboardObject>>() {
            }.getType();
            soundboardList = gson.fromJson(string, type);
            return soundboardList;
        } else {
            ArrayList<MainActivitySoundboardObject> soundboardList = new ArrayList<>();
            return soundboardList;
        }
    }

    private void rebuildRecyclerView() {
        // Get a fresh list from Shared Prefs after a change has been made
        soundboardNamesList = new ArrayList<>();
        soundboardNamesList = getSoundboardsFromSharedPrefs();
        buildRecyclerView();
    }
}
