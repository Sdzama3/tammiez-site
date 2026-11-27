package com.teccalls.recorder

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/** Asked once on first launch, and reachable later from the main screen. */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val group = findViewById<RadioGroup>(R.id.destGroup)
        val phone = findViewById<RadioButton>(R.id.destPhone)
        val email = findViewById<RadioButton>(R.id.destEmail)
        val emailBox = findViewById<LinearLayout>(R.id.emailBox)
        val sender = findViewById<EditText>(R.id.senderEmail)
        val pw = findViewById<EditText>(R.id.appPassword)
        val recipient = findViewById<EditText>(R.id.recipient)
        val autoRec = findViewById<CheckBox>(R.id.autoRecordCalls)
        val save = findViewById<Button>(R.id.saveButton)

        autoRec.isChecked = Prefs.autoRecordCalls(this)
        if (Prefs.destination(this) == Prefs.DEST_EMAIL) email.isChecked = true else phone.isChecked = true
        emailBox.visibility = if (email.isChecked) View.VISIBLE else View.GONE
        sender.setText(Prefs.senderEmail(this))
        pw.setText(Prefs.appPassword(this))
        recipient.setText(Prefs.recipient(this))

        group.setOnCheckedChangeListener { _, checkedId ->
            emailBox.visibility = if (checkedId == R.id.destEmail) View.VISIBLE else View.GONE
        }

        save.setOnClickListener {
            val dest = if (email.isChecked) Prefs.DEST_EMAIL else Prefs.DEST_PHONE
            if (dest == Prefs.DEST_EMAIL &&
                (sender.text.isBlank() || pw.text.isBlank() || recipient.text.isBlank())
            ) {
                Toast.makeText(this, "Fill in all three email fields first.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            Prefs.save(
                this, dest,
                sender.text.toString().trim(),
                pw.text.toString().trim(),
                recipient.text.toString().trim()
            )
            Prefs.setAutoRecordCalls(this, autoRec.isChecked)
            Toast.makeText(this, "Saved.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Don't trap a first-run user who backs out: default to phone-only.
        if (!Prefs.isConfigured(this)) {
            Prefs.save(this, Prefs.DEST_PHONE, "", "", "")
        }
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }
}
