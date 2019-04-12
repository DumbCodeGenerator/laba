package ru.krogon500.laba

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat.startActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import java.io.File


class ImagesAdapter(private val mContext: Context, private val images: ArrayList<String>):
    RecyclerView.Adapter<ImagesAdapter.MyViewHolder>() {
    class MyViewHolder(val imageView: ImageView): RecyclerView.ViewHolder(imageView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val imageView = LayoutInflater.from(parent.context)
            .inflate(R.layout.images_item, parent, false) as ImageView
        return MyViewHolder(imageView)
    }

    override fun getItemCount(): Int {
        return images.size
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val displayMetrics = mContext.resources.displayMetrics
        val screenWidthDp = (displayMetrics.widthPixels / displayMetrics.density).toInt()
        holder.imageView.minimumWidth = screenWidthDp/3
        holder.imageView.setImageBitmap(BitmapFactory.decodeFile(images[position]))
        holder.imageView.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW)
            val uri = FileProvider.getUriForFile(mContext, mContext.packageName, File(images[position]))
            intent.data = uri
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
            startActivity(mContext, intent, null)
        }
    }

    fun addImage(path: String){
        images.add(path)
        notifyDataSetChanged()
    }

}