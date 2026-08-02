package Ir.co.tfs.farazaman.data.db.room

//import androidx.room.Database
//import androidx.room.RoomDatabase
import Ir.co.tfs.farazaman.data.db.dao.UserDao
import Ir.co.tfs.farazaman.data.db.model.UserEntity

//@Database(entities = [UserEntity::class], version = 1)
abstract class AppDatabase
//    : RoomDatabase()
{
    abstract fun userDao(): UserDao
}
