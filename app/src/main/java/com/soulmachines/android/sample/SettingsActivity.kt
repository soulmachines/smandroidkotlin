// Copyright 2022 Soul Machines Ltd

package com.soulmachines.android.sample

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat


class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (supportFragmentManager.findFragmentById(android.R.id.content) == null) {
            supportFragmentManager.beginTransaction()
                .add(android.R.id.content, ConfigurationFragment(this)).commit()
        }
    }
}


class ConfigurationFragment(val activity: SettingsActivity) : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {

    lateinit var sharedPreferences: SharedPreferences

    private val existingSummary = hashMapOf<String, String>()

    private val providedConnectionRelatedPrefs = mutableListOf<Preference>()
    private val providedConnectionRelatedPrefsKeys = setOf<String>("providedConnection")
    private var apiKeyPref : Preference? = null

    private val selfSignedStrategyRelatedPrefs = mutableListOf<Preference>()
    private val selfSignedStrategyRelatedPrefsKeys = setOf<String>(KEY_NAME, PRIVATE_KEY, USE_ORCHESTRATION_SERVER, ORCHESTRATION_SERVER_URL)

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.configuration, rootKey)

        sharedPreferences = preferenceManager.sharedPreferences!!

        //save all existing summary definitions
        val preferencesMap: Map<String?, *> = sharedPreferences.getAll()
        for (preference in preferencesMap.entries) {
            val preferenceEntry = findPreference<Preference>(preference.key as String)

            preferenceEntry.let {
                if(selfSignedStrategyRelatedPrefsKeys.contains(it!!.key)) {
                    selfSignedStrategyRelatedPrefs.add(it)
                }
                if(it is EditTextPreference) {
                    existingSummary.put(it.key as String, it!!.summary as String)
                }
                if(providedConnectionRelatedPrefsKeys.contains(it!!.key)) {
                    providedConnectionRelatedPrefs.add(it)
                }

                if(it.key.equals(API_KEY)) {
                    apiKeyPref = it
                }

                if(it.key.equals(USE_EXISTING_JWT_TOKEN)) {
                    (it as SwitchPreferenceCompat).setOnPreferenceChangeListener { _, newValue ->
                        changeStateOfSelfSignedRelatedProperties((newValue as Boolean))
                        true
                    }
                }
                if(it.key.equals(USE_PROVIDED_CONNECTION)) {
                    (it as SwitchPreferenceCompat).setOnPreferenceChangeListener { _, newValue ->
                        changeStateOfProvidedConnectionRelatedProperties((newValue as Boolean))
                        true
                    }
                }
            }
        }

        //set initial disabled state of the self gen related properties
        val useJWTProp = findPreference<Preference>(USE_EXISTING_JWT_TOKEN)
        changeStateOfSelfSignedRelatedProperties((useJWTProp as SwitchPreferenceCompat).isChecked)

        //set initial disabled state of the self gen related properties
        val useProvidedConfig = findPreference<Preference>(USE_PROVIDED_CONNECTION)
        changeStateOfProvidedConnectionRelatedProperties((useProvidedConfig as SwitchPreferenceCompat).isChecked)


        val button = preferenceManager.findPreference<Preference>("applyChanges")
        if (button != null) {
            button.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                openMainActivity()
                true
            }
        }
    }

    private fun changeStateOfSelfSignedRelatedProperties(useJwtToken: Boolean) {
        for (pref in selfSignedStrategyRelatedPrefs) {
            pref.isEnabled = !useJwtToken
        }
    }

    private fun changeStateOfProvidedConnectionRelatedProperties(useProvidedConnection: Boolean) {
        for (pref in providedConnectionRelatedPrefs) {
            pref.isEnabled = useProvidedConnection
        }

        apiKeyPref?.let {
            it.isEnabled = !useProvidedConnection
        }
    }

    private fun openMainActivity() {
        val newIntent = Intent(activity, MainActivity::class.java)
        newIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        activity.startActivity(newIntent)
        activity.finish()
    }


    override fun onResume() {
        super.onResume()

        // we want to watch the preference values' changes
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
        val preferencesMap: Map<String?, *> = sharedPreferences.getAll()
        // iterate through the preference entries and update their summary if they are an instance of EditTextPreference
        for (preference in preferencesMap.entries) {
            val preferenceEntry = findPreference<Preference>(preference.key as String)
            if (preferenceEntry is EditTextPreference) {
                updateSummary(preferenceEntry as EditTextPreference)
            }
        }
    }

    override fun onPause() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        super.onPause()
    }

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences,
        key: String
    ) {
        val preferencesMap = sharedPreferences.all

        // get the preference that has been changed
        val changedPreference = preferencesMap[key]

        val pref = findPreference<Preference>(key)
        // and if it's an instance of EditTextPreference class, update its summary
        if (pref is EditTextPreference) {
            updateSummary(pref)
        }
    }

    private fun updateSummary(preference: EditTextPreference?) {
        // set the EditTextPreference's summary value to its current text
        val text = preference!!.getText()
        if(!text.isNullOrEmpty()) {
            preference!!.setSummary(text)
        } else {
            preference!!.setSummary(existingSummary.get(preference.key))
        }

    }



    companion object Constants {
        val PRIVATE_KEY = "connectionConfigPrivateKey"
        val KEY_NAME = "connectionConfigKeyName"
        val CONNECTION_URL = "connectionUrl"
        val USE_EXISTING_JWT_TOKEN = "useExistingJWTToken"
        val JWT_TOKEN = "connectionConfigJWT"
        val ORCHESTRATION_SERVER_URL = "orchestrationServerURL"
        val USE_ORCHESTRATION_SERVER = "useOrchestrationServer"
        val USE_PROVIDED_CONNECTION = "useProvidedConnectionConfig"
        val API_KEY = "connectionConfigApiKey"
    }
}