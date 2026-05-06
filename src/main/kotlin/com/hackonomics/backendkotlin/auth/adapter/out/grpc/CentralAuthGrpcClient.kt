package com.hackonomics.backendkotlin.auth.adapter.out.grpc

import auth.v1.AuthServiceGrpcKt
import auth.v1.LoginRequest
import auth.v1.LoginResponse
import auth.v1.LogoutRequest
import auth.v1.RefreshRequest
import auth.v1.RefreshResponse
import auth.v1.SignupRequest
import auth.v1.SignupResponse
import io.grpc.ManagedChannelBuilder
import io.grpc.Metadata
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

private val log = LoggerFactory.getLogger(CentralAuthGrpcClient::class.java)

// gRPC client for Central-Auth's AuthService (:50051).
// Used by AuthController to proxy Login/Signup/Refresh/Logout operations.
@Component
class CentralAuthGrpcClient(
    @Value("\${ai-service.grpc.target:localhost:50051}") private val target: String,
    @Value("\${central-auth.service-key:internal-service-key}") private val serviceKey: String,
) {
    private val channel = ManagedChannelBuilder.forTarget(target)
        .usePlaintext()
        .build()

    private val stub = AuthServiceGrpcKt.AuthServiceCoroutineStub(channel)

    private fun authMetadata(): Metadata = Metadata().apply {
        put(Metadata.Key.of("x-service-key", Metadata.ASCII_STRING_MARSHALLER), serviceKey)
    }

    suspend fun signup(email: String, password: String): SignupResponse =
        stub.signup(
            SignupRequest.newBuilder().setEmail(email).setPassword(password).build(),
            authMetadata(),
        )

    suspend fun login(request: LoginRequest): LoginResponse =
        stub.login(request, authMetadata())

    suspend fun refresh(refreshToken: String): RefreshResponse =
        stub.refresh(
            RefreshRequest.newBuilder().setRefreshToken(refreshToken).build(),
            authMetadata(),
        )

    suspend fun logout(refreshToken: String) =
        stub.logout(
            LogoutRequest.newBuilder().setRefreshToken(refreshToken).build(),
            authMetadata(),
        )

    @PreDestroy
    fun shutdown() {
        log.info("Shutting down CentralAuthGrpcClient channel")
        channel.shutdown()
    }
}
