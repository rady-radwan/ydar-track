package com.rady.worktracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rady.worktracker.TrackerViewModel
import java.text.DateFormat
import java.util.Date

/** Settings card: one account, so the phone and the Windows app share the same data. */
@Composable
fun CloudSyncCard(vm: TrackerViewModel) {
    val st by vm.cloudStatus.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPw by rememberSaveable { mutableStateOf(false) }

    Slab {
        SectionTitle("Sync between devices")
        Text(
            "Sign in with the same account in the Android and Windows apps. Your projects, sessions, goals and the running timer stay the same on both.",
            style = MaterialTheme.typography.bodySmall, color = P.inkMuted,
        )
        Spacer(Modifier.height(12.dp))

        if (st.signedIn) {
            Text("✅ Signed in as ${st.email ?: ""}", style = MaterialTheme.typography.titleSmall, color = P.ink)
            Text(
                if (st.lastSyncMs > 0) "Last sync: ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(st.lastSyncMs))}"
                else "Not synced yet",
                style = MaterialTheme.typography.bodySmall, color = P.inkMuted,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { vm.syncNow() }, enabled = !st.syncing, modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = P.accent, contentColor = P.onAccent),
                ) { Text(if (st.syncing) "Syncing…" else "Sync now", style = MaterialTheme.typography.titleSmall) }
                OutlinedButton(
                    onClick = { vm.cloudSignOut() }, enabled = !st.syncing, modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("Sign out", style = MaterialTheme.typography.titleSmall, color = P.ink) }
            }
            Text(
                "The app also syncs when you open it and a few seconds after each change.",
                style = MaterialTheme.typography.bodySmall, color = P.inkMuted, modifier = Modifier.fillMaxWidth(),
            )
        } else {
            OutlinedTextField(
                value = email, onValueChange = { email = it.trim() }, label = { Text("Email") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = password, onValueChange = { password = it }, label = { Text("Password (6+ characters)") }, singleLine = true,
                visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = { TextButton(onClick = { showPw = !showPw }) { Text(if (showPw) "Hide" else "Show") } },
                shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            val ok = email.contains("@") && password.length >= 6 && !st.syncing
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { vm.cloudSignIn(email, password, create = false) }, enabled = ok,
                    modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = P.accent, contentColor = P.onAccent),
                ) { Text("Sign in", style = MaterialTheme.typography.titleSmall) }
                OutlinedButton(
                    onClick = { vm.cloudSignIn(email, password, create = true) }, enabled = ok,
                    modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(14.dp),
                ) { Text("Create account", style = MaterialTheme.typography.titleSmall, color = P.ink) }
            }
            TextButton(onClick = { vm.cloudResetPassword(email) }, enabled = email.contains("@")) {
                Text("Forgot password?", color = P.info)
            }
        }

        if (st.syncing) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = P.accent)
                Spacer(Modifier.width(8.dp))
                Text("Working…", style = MaterialTheme.typography.bodySmall, color = P.inkMuted)
            }
        }
        st.message?.let {
            Spacer(Modifier.height(6.dp))
            Text((if (st.error) "⚠️ " else "") + it, style = MaterialTheme.typography.bodySmall, color = if (st.error) P.danger else P.info)
        }
    }
}
