// Copyright 2022 Soul Machines Ltd

package com.soulmachines.android.sample

import com.soulmachines.android.smsdk.core.scene.Content
import com.soulmachines.android.smsdk.core.scene.Rect

class ContentImpl(private val bounds: Rect) : Content {
    private val id = "object-" + Integer.toString(uniqueId++)
    override fun getId(): String {
        return id
    }

    override fun getMeta(): Map<String, Any>? {
        return null
    }

    override fun getRect(): Rect {
        return bounds
    }

    companion object {
        var uniqueId = 1
    }
}
