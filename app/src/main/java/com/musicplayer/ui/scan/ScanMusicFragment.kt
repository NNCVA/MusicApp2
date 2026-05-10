package com.musicplayer.ui.scan

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.musicplayer.MusicPlayerApplication
import com.musicplayer.R
import com.musicplayer.data.model.Song
import com.musicplayer.data.repository.MusicRepository
import com.musicplayer.databinding.FragmentScanBinding
import com.musicplayer.ui.adapter.SelectedFolderAdapter
import com.musicplayer.util.media.MusicScanner
import com.musicplayer.util.system.PermissionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 扫描音乐页面Fragment
 */
class ScanMusicFragment : Fragment() {

    private var _binding: FragmentScanBinding? = null
    private val binding get() = _binding!!

    private lateinit var musicRepository: MusicRepository
    private lateinit var folderAdapter: SelectedFolderAdapter

    // 权限请求
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            binding.contentScanMusic.permissionView.visibility = View.GONE
            binding.contentScanMusic.contentView.visibility = View.VISIBLE
        } else {
            showGoToSettingsDialog()
        }
    }

    // 文件夹选择
    private val selectFolderLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == AppCompatActivity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                requireActivity().contentResolver.takePersistableUriPermission(uri, takeFlags)

                val folderPath = getPathFromUri(uri)
                if (folderPath != null) {
                    saveSelectedFolder(folderPath)
                }

                updateSelectedFolders()
                scanSelectedFolders()
            }
        }
    }

    /**
     * Fragment创建时的初始化方法
     *
     * 从应用程序上下文中获取音乐仓库实例，用于后续的音乐数据操作
     *
     * @param savedInstanceState 之前保存的实例状态Bundle，如果是首次创建则为null
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        musicRepository = (requireActivity().application as MusicPlayerApplication).musicRepository
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 设置UI
        setupUI()

        // 检查权限
        checkPermissions()
    }

    private fun setupUI() {
        folderAdapter = SelectedFolderAdapter(requireContext(), emptyList()) { folderUri ->
            removeSelectedFolder(folderUri)
        }

        binding.contentScanMusic.rvSelectedFolders.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = folderAdapter
        }

        binding.contentScanMusic.btnScan.setOnClickListener {
            if (PermissionManager.hasAudioPermission(requireContext())) {
                startScan()
            } else {
                requestPermissions()
            }
        }

        binding.contentScanMusic.btnSelectFolder.setOnClickListener {
            selectFolder()
        }

        updateSelectedFolders()
    }

    // 检查权限
    private fun checkPermissions() {
        if (!PermissionManager.hasAudioPermission(requireContext())) {
            binding.contentScanMusic.permissionView.visibility = View.VISIBLE
            binding.contentScanMusic.contentView.visibility = View.GONE

            binding.contentScanMusic.btnGrantPermission.setOnClickListener {
                requestPermissions()
            }
        } else {
            binding.contentScanMusic.permissionView.visibility = View.GONE
            binding.contentScanMusic.contentView.visibility = View.VISIBLE
        }
    }

    // 请求权限
    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    // 选择文件夹
    private fun selectFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        selectFolderLauncher.launch(intent)
    }

    // 获取路径
    private fun hasSelectedFolders(): Boolean {
        val sharedPref = requireContext().getSharedPreferences("ScanMusicPrefs", Context.MODE_PRIVATE)
        val selectedFoldersJson = sharedPref.getString("selected_folders", null)
        return !selectedFoldersJson.isNullOrBlank() && selectedFoldersJson.split("|")
            .filter { it.isNotBlank() }
            .isNotEmpty()
    }

    // 开始扫描
    private fun startScan() {
        if (hasSelectedFolders()) {
            Toast.makeText(requireContext(), R.string.scanning_selected_folders, Toast.LENGTH_SHORT).show()
            scanSelectedFolders()
        } else {
            Toast.makeText(requireContext(), R.string.scanning_all_music, Toast.LENGTH_SHORT).show()
            scanAllMusic()
        }
    }

    private fun scanAllMusic() {
        binding.contentScanMusic.progressBar.visibility = View.VISIBLE
        binding.contentScanMusic.btnScan.isEnabled = false
        binding.contentScanMusic.tvStatus.text = getString(R.string.scanning)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val songs = MusicScanner.scanAllMusic(requireContext())

                // 如果扫描结果不为空，则插入数据库
                if (songs.isNotEmpty()) {
                    musicRepository.deleteAllSongs()
                    musicRepository.insertSongs(songs)
                }

                //  扫描完成
                withContext(Dispatchers.Main) {
                    onScanComplete(songs.size)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onScanError(e.message ?: "扫描失败")
                }
            }
        }
    }

    /**
     * 扫描选中的文件夹
     */
    private fun scanSelectedFolders() {
        binding.contentScanMusic.progressBar.visibility = View.VISIBLE
        binding.contentScanMusic.btnScan.isEnabled = false
        binding.contentScanMusic.tvStatus.text = getString(R.string.scanning)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val sharedPref = requireContext().getSharedPreferences("ScanMusicPrefs", Context.MODE_PRIVATE)
                val selectedFoldersJson = sharedPref.getString("selected_folders", null)

                val selectedFolders = if (selectedFoldersJson != null) {
                    selectedFoldersJson.split("|").filter { it.isNotBlank() }
                } else {
                    emptyList()
                }

                if (selectedFolders.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        onScanError("未选择任何文件夹")
                    }
                    return@launch
                }

                val allSongs = mutableSetOf<Song>()
                for (uriString in selectedFolders) {
                    try {
                        val songs = MusicScanner.scanFolder(requireContext(), uriString)
                        allSongs.addAll(songs)
                    } catch (e: Exception) {
                        // 忽略单个文件夹的扫描错误
                    }
                }

                if (allSongs.isNotEmpty()) {
                    musicRepository.deleteAllSongs()
                    musicRepository.insertSongs(allSongs.toList())
                }

                withContext(Dispatchers.Main) {
                    onScanComplete(allSongs.size)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onScanError(e.message ?: "扫描失败")
                }
            }
        }
    }

    /**
     * 扫描完成，显示结果，弹出对话框
     */
    private fun onScanComplete(songCount: Int) {
        binding.contentScanMusic.progressBar.visibility = View.GONE
        binding.contentScanMusic.btnScan.isEnabled = true

        if (songCount > 0) {
            binding.contentScanMusic.tvStatus.text = getString(R.string.scan_complete, songCount)
            showSuccessDialog(songCount)
        } else {
            binding.contentScanMusic.tvStatus.text = getString(R.string.no_music_found)
        }
    }

    /**
     * 扫描错误，显示错误信息，弹出对话框
     */
    private fun onScanError(errorMessage: String) {
        binding.contentScanMusic.progressBar.visibility = View.GONE
        binding.contentScanMusic.btnScan.isEnabled = true
        binding.contentScanMusic.tvStatus.text = "扫描失败: $errorMessage"
    }

    private fun showSuccessDialog(songCount: Int) {
        AlertDialog.Builder(requireContext())
            .setTitle("扫描完成")
            .setMessage("共找到 $songCount 首歌曲")
            .setPositiveButton("确定") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun updateSelectedFolders() {
        val sharedPref = requireContext().getSharedPreferences("ScanMusicPrefs", Context.MODE_PRIVATE)
        val selectedFoldersJson = sharedPref.getString("selected_folders", null)

        val selectedFolders = if (selectedFoldersJson != null) {
            selectedFoldersJson.split("|").filter { it.isNotBlank() }
        } else {
            emptyList()
        }

        if (selectedFolders.isEmpty()) {
            binding.contentScanMusic.rvSelectedFolders.visibility = View.GONE
            binding.contentScanMusic.tvNoFolders.visibility = View.VISIBLE
        } else {
            binding.contentScanMusic.rvSelectedFolders.visibility = View.VISIBLE
            binding.contentScanMusic.tvNoFolders.visibility = View.GONE
            folderAdapter.updateFolders(selectedFolders)
        }
    }

    private fun getPathFromUri(uri: Uri): String? {
        return uri.toString()
    }

    private fun saveSelectedFolder(folderPath: String) {
        val sharedPref = requireContext().getSharedPreferences("ScanMusicPrefs", Context.MODE_PRIVATE)
        val selectedFoldersJson = sharedPref.getString("selected_folders", null)

        val selectedFolders = if (selectedFoldersJson != null) {
            selectedFoldersJson.split("|").toMutableList()
        } else {
            mutableListOf()
        }

        if (!selectedFolders.contains(folderPath)) {
            selectedFolders.add(folderPath)
        }

        with(sharedPref.edit()) {
            putString("selected_folders", selectedFolders.joinToString("|"))
            apply()
        }
    }

    private fun removeSelectedFolder(folderPath: String) {
        val sharedPref = requireContext().getSharedPreferences("ScanMusicPrefs", Context.MODE_PRIVATE)
        val selectedFoldersJson = sharedPref.getString("selected_folders", null)

        val selectedFolders = if (selectedFoldersJson != null) {
            selectedFoldersJson.split("|").toMutableList()
        } else {
            mutableListOf()
        }

        selectedFolders.remove(folderPath)

        with(sharedPref.edit()) {
            putString("selected_folders", selectedFolders.joinToString("|"))
            apply()
        }

        updateSelectedFolders()
    }

    private fun showGoToSettingsDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("权限设置")
            .setMessage("请在应用设置中开启存储权限")
            .setPositiveButton("去设置") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.fromParts("package", requireActivity().packageName, null)
                startActivity(intent)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroyView() {
        binding.contentScanMusic.rvSelectedFolders.adapter = null
        super.onDestroyView()
        _binding = null
    }
}
