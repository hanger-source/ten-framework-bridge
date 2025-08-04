import { useState, useCallback } from 'react'
import { RemoteAudioTrack, RemoteVideoTrack } from 'dingrtc'
import { aliRtcManager } from '@/manager/rtc/ali-rtc'
import { IAliRtcUser } from '@/manager/rtc/ali-types'

export function useRTCUsers(useTrulienceAvatar: boolean = false) {
    const [remoteUsers, setRemoteUsers] = useState<IAliRtcUser[]>([])
    const [currentRemoteUser, setCurrentRemoteUser] = useState<IAliRtcUser | undefined>()
    const [isConnected, setIsConnected] = useState(false)

    const onRemoteUserChanged = useCallback((userId: string, userName?: string, audioTrack?: RemoteAudioTrack, videoTrack?: RemoteVideoTrack) => {
        console.log("[useRTCUsers] onRemoteUserChanged", {
            userId,
            userName,
            hasVideoTrack: !!videoTrack,
            hasAudioTrack: !!audioTrack,
            videoTrackType: videoTrack?.constructor.name,
            audioTrackType: audioTrack?.constructor.name,
            videoTrackPlaying: videoTrack?.isPlaying,
            audioTrackPlaying: audioTrack?.isPlaying
        })

        // 处理 Trulience Avatar 逻辑
        if (useTrulienceAvatar) {
            // trulience SDK will play audio in synch with mouth
            audioTrack?.stop()
        }

        // 更新远程用户列表
        setRemoteUsers(prevUsers => {
            const existingUserIndex = prevUsers.findIndex(u => u.userId === userId)

            if (existingUserIndex >= 0) {
                // 更新现有用户
                const updatedUsers = [...prevUsers]
                updatedUsers[existingUserIndex] = {
                    ...updatedUsers[existingUserIndex],
                    userName,
                    audioTrack,
                    videoTrack
                }
                return updatedUsers
            } else {
                // 添加新用户（即使没有音视频轨道也要添加）
                return [...prevUsers, { userId, userName, audioTrack, videoTrack }]
            }
        })

        // 保持向后兼容性，设置第一个有音频或视频轨道的用户为当前远程用户
        if (audioTrack || videoTrack) {
            setCurrentRemoteUser({ userId, userName, audioTrack, videoTrack })
        }
    }, [])

    const onRemoteUserLeft = useCallback((userId: string) => {
        console.log("[useRTCUsers] onRemoteUserLeft", { userId })

        // 从远程用户列表中移除离开的用户
        setRemoteUsers(prevUsers => {
            return prevUsers.filter(u => u.userId !== userId)
        })

        // 如果当前远程用户离开，清空当前远程用户
        setCurrentRemoteUser(prevUser => {
            if (prevUser?.userId === userId) {
                return undefined
            }
            return prevUser
        })
    }, [])

    const onConnectionStateChanged = useCallback((state: string) => {
        const connected = state === 'connected'
        setIsConnected(connected)

        if (!connected) {
            // 连接断开时清理远程用户
            setRemoteUsers([])
            setCurrentRemoteUser(undefined)
        }
    }, [])

    const setupEventListeners = useCallback(() => {
        aliRtcManager.on("remoteUserChanged", onRemoteUserChanged)
        aliRtcManager.on("remoteUserLeft", onRemoteUserLeft)
        aliRtcManager.on("connectionStateChanged", onConnectionStateChanged)
    }, [onRemoteUserChanged, onRemoteUserLeft, onConnectionStateChanged])

    const cleanupEventListeners = useCallback(() => {
        aliRtcManager.off("remoteUserChanged", onRemoteUserChanged)
        aliRtcManager.off("remoteUserLeft", onRemoteUserLeft)
        aliRtcManager.off("connectionStateChanged", onConnectionStateChanged)
    }, [onRemoteUserChanged, onRemoteUserLeft, onConnectionStateChanged])

    const clearUsers = useCallback(() => {
        setRemoteUsers([])
        setCurrentRemoteUser(undefined)
    }, [])

    return {
        remoteUsers,
        currentRemoteUser,
        isConnected,
        setupEventListeners,
        cleanupEventListeners,
        clearUsers
    }
}