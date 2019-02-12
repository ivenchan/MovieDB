package com.kshitijchauhan.haroldadmin.moviedb.repository.movies

import com.kshitijchauhan.haroldadmin.moviedb.repository.data.Resource
import com.kshitijchauhan.haroldadmin.moviedb.repository.data.remote.service.account.*
import com.kshitijchauhan.haroldadmin.moviedb.repository.data.remote.service.movie.MovieService
import com.kshitijchauhan.haroldadmin.moviedb.repository.data.remote.service.search.SearchService
import com.kshitijchauhan.haroldadmin.moviedb.repository.data.remote.utils.NetworkResponse
import com.kshitijchauhan.haroldadmin.moviedb.utils.extensions.*
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers

class RemoteMoviesSource(
    private val movieService: MovieService,
    private val accountService: AccountService,
    private val searchService: SearchService
) {

    fun getMovieDetails(id: Int): Single<Resource<Movie>> {
        return movieService.getMovieDetails(id)
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.computation())
            .flatMap { movieResponse ->
                Single.just(
                    when (movieResponse) {
                        is NetworkResponse.Success -> {
                            Resource.Success(movieResponse.body.toMovie())
                        }
                        is NetworkResponse.ServerError -> {
                            Resource.Error<Movie>(movieResponse.body?.statusMessage ?: "Server Error")
                        }
                        is NetworkResponse.NetworkError -> {
                            Resource.Error(movieResponse.error.localizedMessage ?: "Network Error")
                        }
                    }
                )
            }
    }

    fun getMovieAccountStates(movieId: Int): Single<Resource<AccountState>> {
        return movieService.getAccountStatesForMovie(movieId)
            .subscribeOn(Schedulers.io())
            .flatMap { accountStateResponse ->
                Single.just(
                    when (accountStateResponse) {
                        is NetworkResponse.Success -> {
                            Resource.Success(accountStateResponse.body.toAccountState(movieId))
                        }
                        is NetworkResponse.ServerError -> {
                            Resource.Error<AccountState>(accountStateResponse.body?.statusMessage ?: "Server Error")
                        }
                        is NetworkResponse.NetworkError -> {
                            Resource.Error(accountStateResponse.error.localizedMessage ?: "Network Error")
                        }
                    }
                )
            }
    }

    fun getMovieCast(movieId: Int): Single<Resource<Cast>> {
        return movieService.getCreditsForMovie(movieId)
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.computation())
            .flatMap { response ->
                Single.just(
                    when (response) {
                        is NetworkResponse.Success -> {
                            Resource.Success(
                                Cast(
                                    castMembersIds = response.body.cast.map { it.id },
                                    movieId = movieId
                                ).apply {
                                    castMembers = response.body.cast.map { it.toActor() }
                                }
                            )
                        }
                        is NetworkResponse.ServerError -> {
                            Resource.Error<Cast>(response.body?.statusMessage ?: "Server Error")
                        }
                        is NetworkResponse.NetworkError -> {
                            Resource.Error(response.error.localizedMessage ?: "Server Error")
                        }
                    }
                )
            }
    }

    fun getMovieTrailer(movieId: Int): Flowable<Resource<MovieTrailer>> {
        return movieService.getVideosForMovie(movieId)
            .subscribeOn(Schedulers.io())
            .flatMapPublisher { movieVideosResponse ->
                Flowable.just(when (movieVideosResponse) {
                    is NetworkResponse.Success -> {
                        val trailer = movieVideosResponse
                            .body
                            .results
                            .filter { it.site == "YouTube" && it.type == "Trailer" }
                            .map { it.toMovieTrailer(movieId) }
                            .firstOrDefault(MovieTrailer(movieId, ""))
                        Resource.Success(trailer)
                    }
                    is NetworkResponse.ServerError -> {
                        Resource.Error<MovieTrailer>(movieVideosResponse.body?.statusMessage ?: "Server Error")
                    }
                    is NetworkResponse.NetworkError -> {
                        Resource.Error(movieVideosResponse.error.localizedMessage ?: "Network Error")
                    }
                })
            }
    }

    fun toggleMovieFavouriteStatus(
        isFavourite: Boolean,
        movieId: Int,
        accountId: Int
    ): Single<ToggleFavouriteResponse> {
        return ToggleMediaFavouriteStatusRequest(
            MediaTypes.MOVIE.mediaName,
            movieId,
            isFavourite
        ).let { request ->
            accountService.toggleMediaFavouriteStatus(accountId, request)
                .subscribeOn(Schedulers.io())
        }
    }

    fun toggleMovieWatchlistStatus(
        isWatchlisted: Boolean,
        movieId: Int,
        accountId: Int
    ): Single<ToggleWatchlistResponse> {
        return ToggleMediaWatchlistStatusRequest(
            MediaTypes.MOVIE.mediaName,
            movieId,
            isWatchlisted
        ).let { request ->
            accountService.toggleMediaWatchlistStatus(accountId, request)
                .subscribeOn(Schedulers.io())
        }
    }

    fun getSearchResultsForQuery(query: String): Single<List<Movie>> {
        return searchService
            .searchForMovie(query)
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.computation())
            .flatMapPublisher { searchResponse ->
                Flowable.fromIterable(searchResponse.results)
            }
            .map { generalMovieResponse -> generalMovieResponse.toMovie() }
            .toList()
    }
}