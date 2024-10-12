package com.kbyai.facerecognition

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView

class PersonAdapter(context: Context?, personList: ArrayList<Person>) : ArrayAdapter<Person?>(
    context!!, 0, personList!! as List<Person?>
) {
    var dbManager: DBManager = DBManager(context)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var convertView = convertView
        val person = getItem(position)
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_person, parent, false)
        }

        val tvName = convertView!!.findViewById<View>(R.id.textName) as TextView
        val faceView = convertView.findViewById<View>(R.id.imageFace) as ImageView
        convertView.findViewById<View>(R.id.buttonDelete).setOnClickListener {
            dbManager.deletePerson(DBManager.personList[position].name!!)
            notifyDataSetChanged()
        }

        tvName.text = person!!.name
        faceView.setImageBitmap(person.face)
        // Return the completed view to render on screen
        return convertView
    }
}