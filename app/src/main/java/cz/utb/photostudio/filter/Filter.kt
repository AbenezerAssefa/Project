package cz.utb.photostudio.filter

import android.graphics.ColorMatrix
import androidx.fragment.app.Fragment

abstract class Filter(name: String) {

    var filter_Name: String = ""

    protected var changed: (()->Unit)? = null

    init {
        this.filter_Name = name
    }

    /**
     * Assigns the filter on changed callback (it is triggered when a change in the filter occurs)
     */
    fun setOnChangedCallback(changed: (()->Unit)?) {
        this.changed = changed
    }

    /**
     * Sets the filter name
     */
    fun setFilterName(name: String) {
        this.filter_Name = name
    }

    /**
     * Returns the filter name
     */
    fun getFilterName(): String {
        return this.filter_Name
    }

    /**
     * Returns the fragment for controlling the filter
     */
    abstract fun getControllFragment(): Fragment?

    /**
     * Applies the filter to the bitmap
     */
    abstract fun applyFilter(matrix: ColorMatrix)

    /**
     * Resets the filter settings (restores original settings)
     */
    abstract fun reset()

}
