package com.kelsos.mbrc.repository

import com.kelsos.mbrc.data.library.Artist
import com.kelsos.mbrc.repository.data.LocalArtistDataSource
import com.kelsos.mbrc.repository.data.RemoteArtistDataSource
import com.raizlabs.android.dbflow.list.FlowCursorList
import rx.Single
import javax.inject.Inject

class ArtistRepositoryImpl
@Inject constructor(private val localDataSource: LocalArtistDataSource,
                    private val remoteDataSource: RemoteArtistDataSource) : ArtistRepository {


  override fun getArtistByGenre(genre: String): Single<FlowCursorList<Artist>> {
    return localDataSource.getArtistByGenre(genre)
  }

  override fun getAllCursor(): Single<FlowCursorList<Artist>> {
    return localDataSource.loadAllCursor().toSingle()
  }

  override fun getAndSaveRemote(): Single<FlowCursorList<Artist>> {
    localDataSource.deleteAll()
    return remoteDataSource.fetch().doOnNext {
      localDataSource.saveAll(it)
    }.toCompletable().andThen(localDataSource.loadAllCursor().toSingle())
  }
}