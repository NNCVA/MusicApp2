package com.musicplayer.ui.dialog

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.musicplayer.R
import com.musicplayer.data.model.Song
import com.musicplayer.databinding.BottomSheetSongInfoBinding
import com.musicplayer.util.system.FormatUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 歌曲信息BottomSheet
 * 显示歌曲的详细信息
 */
class SongInfoBottomSheet : BottomSheetDialogFragment(), CoroutineScope {

    private lateinit var binding: BottomSheetSongInfoBinding
    private lateinit var song: Song

    private val job = SupervisorJob()
    override val coroutineContext = Dispatchers.Main + job

    companion object {
        const val TAG = "SongInfoBottomSheet"
        private const val ARG_SONG = "song"

        fun newInstance(song: Song): SongInfoBottomSheet {
            return SongInfoBottomSheet().apply {
                arguments = bundleOf(ARG_SONG to song)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        song = requireArguments().getParcelable(ARG_SONG)
            ?: throw IllegalArgumentException("Song is required")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = BottomSheetSongInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        loadSongInfo()
    }

    private fun setupListeners() {
        binding.btnClose.setOnClickListener {
            dismiss()
        }

        binding.btnCopyPath.setOnClickListener {
            copyPathToClipboard()
        }
    }

    private fun loadSongInfo() {
        // 基本信息
        binding.itemTitle.tvLabel.text = getString(R.string.label_title)
        binding.itemTitle.tvValue.text = song.title

        binding.itemArtist.tvLabel.text = getString(R.string.label_artist)
        binding.itemArtist.tvValue.text = song.artist

        binding.itemAlbum.tvLabel.text = getString(R.string.label_album)
        binding.itemAlbum.tvValue.text = song.album

        // 文件信息
        binding.itemPath.tvLabel.text = getString(R.string.label_path)
        binding.itemPath.tvValue.text = song.path

        binding.itemSize.tvLabel.text = getString(R.string.label_size)
        // 异步加载文件大小
        launch {
            val fileSize = FormatUtils.getFileSizeAsync(song.path)
            binding.itemSize.tvValue.text = fileSize
        }

        binding.itemFormat.tvLabel.text = getString(R.string.label_format)
        binding.itemFormat.tvValue.text = FormatUtils.getFileExtension(song.path)

        binding.itemDuration.tvLabel.text = getString(R.string.label_duration)
        binding.itemDuration.tvValue.text = song.getDurationString()

        // 时间信息
        binding.itemDateAdded.tvLabel.text = getString(R.string.label_date_added)
        binding.itemDateAdded.tvValue.text = FormatUtils.formatDate(song.dateAdded)

        binding.itemDateModified.tvLabel.text = getString(R.string.label_date_modified)
        binding.itemDateModified.tvValue.text = FormatUtils.formatDate(song.dateModified)
    }

    private fun copyPathToClipboard() {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("歌曲路径", song.path)
        clipboard.setPrimaryClip(clip)

        Toast.makeText(
            requireContext(),
            getString(R.string.path_copied),
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}
