package app.rota.di

import android.content.Context
import androidx.room.Room
import app.rota.data.local.RotaDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): RotaDatabase =
        Room.databaseBuilder(context, RotaDatabase::class.java, "rota.db")
            .enableMultiInstanceInvalidation()
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun habitDao(db: RotaDatabase) = db.habitDao()

    @Provides
    fun routineDao(db: RotaDatabase) = db.routineDao()

    @Provides
    fun checkInDao(db: RotaDatabase) = db.checkInDao()

    @Provides
    fun snoozeDao(db: RotaDatabase) = db.snoozeDao()

    @Provides
    @Singleton
    fun json(): Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
}
