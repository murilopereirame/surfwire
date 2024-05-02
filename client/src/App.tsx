import React, {useEffect, useState} from "react"
import {callConnect, callDisconnect, callGetStatus, VpnStatus} from "./request";
import {ToastContainer} from "react-toastify";

const App = () => {
    const [isConnected, setIsConnected] = useState<boolean>(false)
    const [status, setStatus] = useState<VpnStatus | undefined>()
    const [isLoading, setIsLoading] = useState<boolean>(true)

    useEffect(() => {
        callGetStatus().then((status) => {
            if (status) {
                setStatus(status)
                setIsConnected(true)
            }
        }).finally(() => setIsLoading(false))
    }, [])

    useEffect(() => {
        let poller;
        if (isConnected) {
            poller !== undefined && clearInterval(poller)
            poller = setInterval(() => {
                callGetStatus().then((status) => {
                    if (status) {
                        setStatus(status)
                        setIsConnected(true)
                    }
                })
            }, 5000)
        } else {
            poller !== undefined && clearInterval(poller)
        }
    }, [isConnected]);

    const generateStatusMessage = () => {
        return `Client pKey: ${status?.client}\n` +
            `Endpoint: ${status?.endpoint}\n` +
            `Routed IPs: ${status?.allowedIps}\n` +
            `Handshake: ${status?.uptime}\n` +
            `Received: ${status?.traffic.received.value.toFixed(2)} ${status?.traffic.received.unit}\n` +
            `Sent: ${status?.traffic.sent.value.toFixed(2)} ${status?.traffic.sent.unit}`

    }

    const switchConnection = async () => {
        setIsLoading(true)
        if (isConnected) {
            setIsConnected(!(await callDisconnect()))
        } else {
            setIsConnected(await callConnect())
        }

        callGetStatus().then((status) => {
            if (status) {
                setStatus(status)
                setIsConnected(true)
            }
        }).finally(() => setIsLoading(false))
    }

    return <>
        {isLoading && <div className="loading-container w-full h-full flex items-center justify-center">
			<div className="lds-ring">
				<div></div>
				<div></div>
				<div></div>
				<div></div>
			</div>
		</div>}
        <ToastContainer/>
        <div className={"flex flex-col items-center h-full justify-center bg-arapawa-900"}>
            <img className={"w-52"} alt={"Surfwire"} src={"assets/logo.png"}/>
            <div className={"w-3/4 flex flex-col items-center mt-5"}>
                <textarea readOnly={true}
                          value={!isConnected ? 'Disconnected' : generateStatusMessage()}
                          style={{minHeight: 190}}
                          className={"text-white max-w-xs w-full bg-transparent border-white resize-none border-2 rounded-xl p-2 cursor-text"}>
                </textarea>
                <button
                    className={`mt-2 max-w-xs font-black w-full ${isConnected ? 'bg-red-700' : 'bg-green-600'} text-xl rounded-xl text-white`}
                    onClick={() => switchConnection()}
                >
                    {isConnected ? 'DISCONNECT' : 'CONNECT'}
                </button>
            </div>
        </div>
    </>
}

export default App