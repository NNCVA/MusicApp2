package com.musicplayer.util.system

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.musicplayer.R

/**
 * 权限管理工具类
 * 处理Android 13+的新权限模型
 */
object PermissionManager {
    
    // Android 13+ 音频权限
    private const val PERMISSION_AUDIO = Manifest.permission.READ_MEDIA_AUDIO
    
    // Android 12及以下存储权限
    private const val PERMISSION_STORAGE = Manifest.permission.READ_EXTERNAL_STORAGE
    
    // 通知权限（Android 13+）
    private const val PERMISSION_NOTIFICATION = Manifest.permission.POST_NOTIFICATIONS
    
    /**
     * 检查音频权限是否已授予
     */
    fun hasAudioPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, PERMISSION_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, PERMISSION_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * 检查通知权限是否已授予
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, PERMISSION_NOTIFICATION) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Android 13以下不需要此权限
        }
    }
    
    /**
     * 请求音频权限（Activity）
     */
    fun requestAudioPermission(activity: Activity, requestCode: Int) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PERMISSION_AUDIO
        } else {
            PERMISSION_STORAGE
        }
        
        ActivityCompat.requestPermissions(activity, arrayOf(permission), requestCode)
    }
    
    /**
     * 请求通知权限（Activity）
     */
    fun requestNotificationPermission(activity: Activity, requestCode: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(activity, arrayOf(PERMISSION_NOTIFICATION), requestCode)
        }
    }
    
    /**
     * 检查是否需要显示权限说明
     */
    fun shouldShowRationale(activity: Activity, permission: String): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }
    
    /**
     * 显示权限说明对话框
     */
    fun showPermissionRationale(
        activity: Activity,
        title: String,
        message: String,
        onPositive: () -> Unit
    ) {
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.confirm) { _, _ -> onPositive() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    /**
     * 显示权限被拒绝的提示
     */
    fun showPermissionDeniedDialog(
        activity: Activity,
        title: String,
        message: String
    ) {
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.confirm, null)
            .show()
    }
    
    /**
     * 处理权限请求结果
     */
    fun handlePermissionResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
        onPermissionGranted: (Int) -> Unit,
        onPermissionDenied: (Int) -> Unit
    ) {
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            onPermissionGranted(requestCode)
        } else {
            onPermissionDenied(requestCode)
        }
    }
    
    /**
     * 使用Activity Result API请求权限（推荐方式）
     */
    class PermissionRequester(private val fragment: Fragment) {
        private var onPermissionGranted: (() -> Unit)? = null
        private var onPermissionDenied: (() -> Unit)? = null
        
        private val permissionLauncher = fragment.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                onPermissionGranted?.invoke()
            } else {
                onPermissionDenied?.invoke()
            }
        }
        
        fun requestPermission(
            permission: String,
            onGranted: () -> Unit,
            onDenied: () -> Unit
        ) {
            onPermissionGranted = onGranted
            onPermissionDenied = onDenied
            permissionLauncher.launch(permission)
        }
    }
    
    /**
     * 获取需要请求的权限列表
     */
    fun getRequiredPermissions(): List<String> {
        val permissions = mutableListOf<String>()
        
        // 音频权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(PERMISSION_AUDIO)
            permissions.add(PERMISSION_NOTIFICATION)
        } else {
            permissions.add(PERMISSION_STORAGE)
        }
        
        return permissions
    }
}