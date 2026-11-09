package corsica.universita.carnet.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Base de données SQLite Room versionnée de v1 à v4 avec toutes ses migrations SQL explicites.
 * Aucun `fallbackToDestructiveMigration` n'est utilisé afin de préserver les plantes des utilisateurs.
 */
@Database(
    entities = [PlantEntity::class],
    version = 4,
    exportSchema = true,
)
abstract class CarnetDatabase : RoomDatabase() {

    abstract fun plantDao(): PlantDao

    companion object {
        /**
         * TD A4 : v1 -> v2 (Ajout de la colonne `pinned` pour épingler une plante prioritaire)
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE plants ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * TD A5 : v2 -> v3 (Ajout de la colonne `synced` pour l'Outbox WorkManager)
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE plants ADD COLUMN synced INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * TD A7 + Projet : v3 -> v4 (Ajout de la position GPS `lat`/`lng`/`accuracy`,
         * du chemin photo `photoPath` et du relevé du capteur de lumière `lastLux`)
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE plants ADD COLUMN lat REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE plants ADD COLUMN lng REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE plants ADD COLUMN accuracy REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE plants ADD COLUMN photoPath TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE plants ADD COLUMN lastLux REAL DEFAULT NULL")
            }
        }

        fun build(context: Context): CarnetDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                CarnetDatabase::class.java,
                "carnet.db",
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}

