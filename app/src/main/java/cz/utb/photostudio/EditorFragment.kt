package cz.utb.photostudio

import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.recyclerview.widget.LinearLayoutManager
import cz.utb.photostudio.databinding.FragmentEditorBinding
import cz.utb.photostudio.filter.Filter
import cz.utb.photostudio.persistent.AppDatabase
import cz.utb.photostudio.persistent.FilterPersistent
import cz.utb.photostudio.persistent.FilterPersistentDao
import cz.utb.photostudio.persistent.ImageFile
import cz.utb.photostudio.util.FilterListAdapter
import cz.utb.photostudio.util.FilterSelectDialog
import cz.utb.photostudio.util.ImageIO
import java.util.*
import java.util.concurrent.Executors

class EditorFragment : Fragment() {

    companion object {
        const val ARG_IMG_UID = "image_uid"
    }

    private var _binding: FragmentEditorBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = requireNotNull(_binding) { "Binding should not be null" }

    // List of filters
    private var filterListAdapter: FilterListAdapter? = null
    private var selectedFilter: Filter? = null

    // The image currently being worked on
    var image: ImageFile? = null
    private var defaultBitmap: Bitmap? = null

    // Dialog for adding a filter
    private var filterSelectDialog: FilterSelectDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            val imgUid = it.getInt(ImageFragment.ARG_IMG_UID)
            loadImageFromDB(imgUid)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        _binding = FragmentEditorBinding.inflate(inflater, container, false)

        // List adapter for filters
        filterListAdapter = FilterListAdapter(requireContext(), LinkedList<Filter>())
        filterListAdapter?.setOnChangedCallback { filter ->
            selectFilter(filter)
        }

        // RecyclerView for the filter list
        val mLayoutManager = LinearLayoutManager(requireContext())
        mLayoutManager.orientation = LinearLayoutManager.HORIZONTAL
        binding.recyclerViewFilters.layoutManager = mLayoutManager
        binding.recyclerViewFilters.adapter = filterListAdapter

        // Dialog for creating a filter
        filterSelectDialog = FilterSelectDialog(activity)
        filterSelectDialog?.setOnSelectCallBack { filter ->
            filter?.let { addFilter(it, true) }
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Add a new filter
        binding.buttonAdd.setOnClickListener {
            filterSelectDialog?.showDialog()
        }

        // Remove the currently selected filter
        binding.buttonDelete.setOnClickListener {
            selectedFilter?.let { removeFilter(it) }
        }

        // Save the image to the gallery along with filters
        binding.buttonExport.setOnClickListener {
            image?.let { ImageIO.exportImageToGallery(requireContext(), it, filterListAdapter?.getFilterList()) }
        }

        // Save filters of the image to the database
        binding.buttonSave.setOnClickListener {
            Executors.newSingleThreadExecutor().execute {
                try {
                    val db: AppDatabase = AppDatabase.getDatabase(requireContext())
                    val filtersDao: FilterPersistentDao = db.filterPersistentDao()
                    // Delete all filters
                    filtersDao.deleteAllWithImageUID(image!!.uid)
                    // Insert again
                    for (filter in filterListAdapter!!.getFilterList()) {
                        val fp: FilterPersistent = FilterPersistent.fromFilter(filter, image!!.uid)
                        filtersDao.insert(fp)
                    }

                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(requireContext(), "Filters saved successfully", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(requireContext(), "Failed to save filters", Toast.LENGTH_SHORT).show()
                    }
                    e.printStackTrace()
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun reloadFxControlView(fragment: Fragment?) {
        val fragmentTransaction: FragmentTransaction = fragmentManager?.beginTransaction() ?: return

        if (fragment != null) {
            fragmentTransaction.replace(R.id.fragment_container_view, fragment)
        } else {
            fragmentTransaction.replace(R.id.fragment_container_view, EmptyFragment())
        }
        fragmentTransaction.addToBackStack(null)
        fragmentTransaction.commit()
    }

    private fun loadImageFromDB(imgUid: Int) {
        Executors.newSingleThreadExecutor().execute {
            try {
                val db: AppDatabase = AppDatabase.getDatabase(requireContext())
                // Load image data
                image = db.imageFileDao().getById(imgUid)
                defaultBitmap = ImageIO.loadImage(requireContext(), image!!.imagePath)

                // Load & parse filters
                val filterList = LinkedList<Filter>()
                val filtersDao: FilterPersistentDao = db.filterPersistentDao()
                for (fp in filtersDao.getAllWithImageUID(image!!.uid)) {
                    val f: Filter? = fp.createFilter()
                    f?.let { filterList.add(it) }
                }

                // Reload image preview and filter list
                Handler(Looper.getMainLooper()).post {
                    // Add all filters
                    for (f in filterList) {
                        addFilter(f, false)
                    }
                    // Reload image preview
                    reloadImagePreview()
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(requireContext(), "Failed to load image", Toast.LENGTH_SHORT).show()
                }
                e.printStackTrace()
            }
        }
    }

    private fun reloadImagePreview() {
        if (defaultBitmap == null) return

        if (defaultBitmap!!.isRecycled) {
            Log.e("EDITOR", "Default bitmap was recycled!!")
            return
        }

        filterListAdapter?.let {
            val matrix = ColorMatrix()

            for (filter in it.getFilterList()) {
                filter.applyFilter(matrix)
            }

            val newBitmap = defaultBitmap!!.config?.let { defaultBitmap!!.copy(it, true) }
            val canvas = newBitmap?.let { Canvas(it) }
            val paint = Paint()
            paint.colorFilter = ColorMatrixColorFilter(matrix)
            canvas?.drawBitmap(defaultBitmap!!, 0f, 0f, paint)

            binding.imagePreview.setImageBitmap(newBitmap)
        } ?: run {
            binding.imagePreview.setImageBitmap(defaultBitmap!!)
        }
    }

    private fun selectFilter(filter: Filter?) {
        selectedFilter = filter
        if (selectedFilter != null) {
            reloadFxControlView(selectedFilter!!.getControllFragment()!!)
        } else {
            reloadFxControlView(null)
        }
    }

    private fun addFilter(filter: Filter, addIndex: Boolean) {
        if (addIndex) {
            var cnt = 1
            for (f in filterListAdapter?.getFilterList()!!) {
                if (f::class == filter::class) {
                    cnt++
                }
            }
            filter.filter_Name += " $cnt"
        }
        filterListAdapter?.addFilter(filter)
        filter.setOnChangedCallback {
            reloadImagePreview()
        }
        selectFilter(filter)
        reloadImagePreview()
    }

    private fun removeFilter(filter: Filter) {
        filterListAdapter?.removeFilter(filter)
        if (selectedFilter == filter) {
            selectFilter(null)
        }
        reloadImagePreview()
    }

    class EmptyFragment : Fragment() {
        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?,
        ): View? {
            return inflater.inflate(R.layout.fragment_empty, container, false)
        }
    }
}
