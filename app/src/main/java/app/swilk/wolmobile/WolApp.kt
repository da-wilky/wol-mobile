package app.swilk.wolmobile

import android.app.Application
import android.content.Context
import app.swilk.wolmobile.data.AppDatabase
import app.swilk.wolmobile.run.createNotificationChannels
import kotlinx.coroutines.MainScope
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

class WolApp : Application() {
    val db by lazy { AppDatabase.open(this) }
    val scope = MainScope()

    override fun onCreate() {
        super.onCreate()
        // Android's built-in "BC" provider is stripped down and lacks the curves sshj needs.
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.addProvider(BouncyCastleProvider())
        createNotificationChannels(this)
    }
}

val Context.db: AppDatabase get() = (applicationContext as WolApp).db
