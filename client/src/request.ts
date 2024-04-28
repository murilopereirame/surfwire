import axios from "axios";
import {toast} from "react-toastify";

export interface VpnStatus {
    "client": string
    "endpoint": string,
    "allowedIps": string,
    "uptime": string,
    "traffic": {
        "received": {
            "value": number,
            "unit": string
        },
        "sent": {
            "value": number,
            "unit": string
        }
    }
}

const callGetStatus = async () => {
    try {
        const response = await axios.get("/status", {timeout: 30000})
        return response.data as VpnStatus
    } catch (e) {
        return null
    }
}

const callConnect = async () => {
    try {
        await axios.post("/connect", {}, {timeout: 30000})
        toast.success("Connected", {
            theme: "dark"
        })

        return true
    } catch (e: any) {
        toast.error(e.data ? e.data.messages.join(". ") : e.toString(), {theme: "dark"})
        return false
    }
}

const callDisconnect = async () => {
    try {
        await axios.post("/disconnect", {}, {timeout: 30000})
        toast.success("Disconnected", {
            theme: "dark"
        })
        return true
    } catch (e: any) {
        toast.error(e.data ? e.data.messages.join(". ") : e.toString(), {theme: "dark"})
        return false
    }
}

export { callGetStatus, callConnect, callDisconnect }