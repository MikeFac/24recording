package tel.fouryou.blackboxreplacement

import android.app.Activity
import android.app.AlertDialog
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.WindowManager
import android.widget.EditText

object StopConfirmation {
    const val ACTION_REQUEST_STOP = "tel.fouryou.blackboxreplacement.action.REQUEST_STOP"

    private const val STOP_CODE = "2468"
    private const val STOP_CODE_LENGTH = 4

    fun show(activity: Activity, onConfirmed: () -> Unit) {
        val input = EditText(activity).apply {
            hint = "4-digit stop code"
            contentDescription = "Enter the four-digit stop code"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            isSingleLine = true
            filters = arrayOf(InputFilter.LengthFilter(STOP_CODE_LENGTH))
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle("Confirm stopping")
            .setMessage("Recording will continue until the correct code is entered.")
            .setView(input)
            .setNegativeButton("Keep recording", null)
            .setPositiveButton("Confirm stop", null)
            .create()

        dialog.setOnShowListener {
            val confirmButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE).apply {
                isEnabled = false
                setOnClickListener {
                    if (input.text.toString() == STOP_CODE) {
                        dialog.dismiss()
                        onConfirmed()
                    } else {
                        input.error = "Incorrect stop code"
                    }
                }
            }
            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                    confirmButton.isEnabled = text?.length == STOP_CODE_LENGTH
                    input.error = null
                }
                override fun afterTextChanged(editable: Editable?) = Unit
            })
            input.requestFocus()
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }
        dialog.show()
    }
}
