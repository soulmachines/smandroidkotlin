// Copyright 2022 Soul Machines Ltd

package com.soulmachines.android.sample

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import java.security.Key
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date


class JWTTokenProvider(context: Context) {

    private val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    fun getJWTToken(success: (jwtToken: String) -> Unit, error: (errorMessage: String) -> Unit) {
        getJwtSource().getJwt(success, error)
    }

    // identify the strategy/implementation to use based on configuration
    internal fun getJwtSource() : JWTSource {
        val accessToken = preferences.getString(ConfigurationFragment.JWT_TOKEN, "")
        val selfSigningKeyName = preferences.getString(ConfigurationFragment.KEY_NAME, "")
        val selfSigningPrivateKey = preferences.getString(ConfigurationFragment.PRIVATE_KEY, "")

        val useOrchServer = preferences.getBoolean(ConfigurationFragment.USE_ORCHESTRATION_SERVER, false)
        val orchestrationServerURL = preferences.getString(ConfigurationFragment.ORCHESTRATION_SERVER_URL, "")

        val useExistingToken = preferences.getBoolean(ConfigurationFragment.USE_EXISTING_JWT_TOKEN, false)

        return when {
           useExistingToken -> PregeneratedJwtSource(accessToken!!)
           !selfSigningKeyName.isNullOrEmpty() && !selfSigningPrivateKey.isNullOrEmpty() -> SelfSigned(
               selfSigningPrivateKey,
               selfSigningKeyName,
               if(useOrchServer) orchestrationServerURL else null
           )
           //default to the token server strategy
           else -> error("The required properties on the Settings Screen must be set.")
       }

    }

}

interface JWTSource {
    fun getJwt(success: (jwtToken: String) -> Unit, error: (errorMessage: String) -> Unit)
}

/** Use a pre-generated JWT token. */
internal class PregeneratedJwtSource(val jwtToken: String) : JWTSource {
    override fun getJwt(
        success: (jwtToken: String) -> Unit,
        error: (errorMessage: String) -> Unit
    ) {
        if(jwtToken.isNotBlank()) {
            success(jwtToken)
        } else {
            error("The specified JWT token (jwt1_connection_access_token) cannot be empty.")
        }
    }

}

/** Using a self signed JWT token. */
internal class SelfSigned(val privateKey: String, val keyName: String, val orchestrationServerUrl: String? = null) : JWTSource {
    override fun getJwt(
        success: (jwtToken: String) -> Unit,
        error: (errorMessage: String) -> Unit
    ) {
        if(privateKey.isNotBlank() && keyName.isNotBlank()) {
            success(generateJwt(privateKey, keyName))
        } else {
            error("The specified private key (jwt3_ddna_studio_key_name) and key name (jwt3_ddna_studio_key_name) properties cannot be empty.")
        }
    }

    private fun generateJwt(privateKey: String, keyName: String): String {
        val key: Key = Keys.hmacShaKeyFor(privateKey.encodeToByteArray())
        val currentTime = Date.from(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant())
        val jwsBuilder = Jwts.builder()
            .setHeaderParam("typ", "JWT")
            .setNotBefore(currentTime)
            .setIssuedAt(currentTime)
            .setExpiration(Date.from(LocalDateTime.now().plusDays(1).atZone(ZoneId.systemDefault()).toInstant()))
            .claim("iss", keyName)

        if(!orchestrationServerUrl.isNullOrEmpty()) {
            // add extra details about the orchestration server
            jwsBuilder
                .claim("sm-control-via-browser", true)
                .claim("sm-control", orchestrationServerUrl)
        }

        return jwsBuilder.signWith(key).compact()
    }

}