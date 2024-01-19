package com.example.sandbox;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Bundle;
import android.system.OsConstants;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    static boolean isVpnMode = false;
    private LinkProperties activeLinkProperties = null;
    private NetworkMonitor networkMonitor;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Hook up VPN mode toggle button
        findViewById(R.id.vpn_mode_toggle_button).setOnClickListener(v -> {
                    isVpnMode = !isVpnMode;
                    // Restart the network monitor if we are at least on Android 5.0 (Lollipop)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && networkMonitor != null) {
                        try {
                            networkMonitor.stop(this);
                            networkMonitor.start(this);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
        );

        // Hook up network interface IPs button
        findViewById(R.id.network_interfaces_ips_button).setOnClickListener(v -> {
            List<String> ipAddresses = getNetworkInterfaceIps();
            Log.d("NetworkInterfaceIps", "Got IPs:" + ipAddresses);
            showIpsDialog("Network Interface IPs", ipAddresses);
        });

        // If we are at least on Android 5.0 (Lollipop) hook up the rest of the buttons
        // and start the network monitor
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Hook up all network IPs button
            findViewById(R.id.all_networks_ips_button).setOnClickListener(v -> {
                List<String> ipAddresses = getAllNetworksIps();
                Log.d("AllNetworksIps", "Got IPs:" + ipAddresses);
                showIpsDialog("All Network IPs", ipAddresses);
            });

            // Hook up active link properties IPs button
            findViewById(R.id.active_link_properties_ips_button).setOnClickListener(v -> {
                List<String> ipAddresses = getActiveLinkPropertiesIps();
                Log.d("ActiveLinkPropertiesIps", "Got IPs:" + ipAddresses);
                showIpsDialog("Active Link Properties IPs", ipAddresses);
            });

            ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            networkMonitor = new NetworkMonitor(network -> {
                try {
                    if (connectivityManager != null && network != null) {
                        activeLinkProperties = connectivityManager.getLinkProperties(network);
                    } else {
                        activeLinkProperties = null;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            try {
                networkMonitor.start(this);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            networkMonitor.stop(this);
        }
    }

    public List<String> getNetworkInterfaceIps() {
        String TAG = "NetworkInterfaceIps";
        List<String> ipAddresses = new ArrayList<>();
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface networkInterface : interfaces) {
                // Log interface parameters
                Log.d(TAG, "interface: " + networkInterface.getName() + ", isUp: " + networkInterface.isUp() +
                        ", isLoopback: " + networkInterface.isLoopback() +
                        ", isPointToPoint: " + networkInterface.isPointToPoint() +
                        ", isVirtual: " + networkInterface.isVirtual() + ", isHardware: " + networkInterface.isVirtual() + ", MTU: " + networkInterface.getMTU());
                List<InetAddress> addresses = Collections.list(networkInterface.getInetAddresses());
                for (InetAddress address : addresses) {
                    // Check if the IP address is not a loopback
                    if (address.isLoopbackAddress()) {
                        Log.d(TAG, address.getHostAddress() + " is a loopback address, skipping...");
                        continue;
                    }
                    // Check if the IP address is not a link-local address
                    if (address.isLinkLocalAddress()) {
                        Log.d(TAG, address.getHostAddress() + " is a link-local address, skipping...");
                        continue;
                    }
                    Log.d(TAG, address.getHostAddress() + " is a valid address, adding...");
                    ipAddresses.add(address.getHostAddress());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting IPs: " + e.getMessage());
        }

        return ipAddresses;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public List<String> getAllNetworksIps() {
        String TAG = "AllNetworksIps";
        List<String> ipAddresses = new ArrayList<>();
        try {
            ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            Network[] networks = connectivityManager.getAllNetworks();
            for (Network network : networks) {
                // Log network parameters
                Log.d(TAG, "network: " + network);
                LinkProperties linkProperties = connectivityManager.getLinkProperties(network);
                // Log link properties
                Log.d(TAG, "linkProperties: " + linkProperties);

                // Check if link properties is null
                if (linkProperties == null) {
                    continue;
                }

                for (LinkAddress linkAddress : linkProperties.getLinkAddresses()) {
                    // Log link address
                    Log.d(TAG, "linkAddress: " + linkAddress);
                    Log.d(TAG, "Checking IP: " + linkAddress.getAddress().getHostAddress());
                    logInterestingLinkAddressFlags(TAG, linkAddress);
                    // Check if the IP address is not a loopback
                    if (linkAddress.getAddress().isLoopbackAddress()) {
                        Log.d(TAG, linkAddress.getAddress().getHostAddress() + " is a loopback address, skipping...");
                        continue;
                    }
                    // Check if the IP address is not a link-local address
                    if (linkAddress.getAddress().isLinkLocalAddress()) {
                        Log.d(TAG, linkAddress.getAddress().getHostAddress() + " is a link-local address, skipping...");
                        continue;
                    }
                    Log.d(TAG, linkAddress.getAddress().getHostAddress() + " is a valid address, adding...");
                    ipAddresses.add(linkAddress.getAddress().getHostAddress());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting IPs: " + e.getMessage());
        }

        return ipAddresses;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public List<String> getActiveLinkPropertiesIps() {
        String TAG = "ActiveLinkPropertiesIps";
        List<String> ipAddresses = new ArrayList<>();
        try {
            // Log active link properties
            Log.d(TAG, "activeLinkProperties: " + activeLinkProperties);

            // Check if active link properties is null
            if (activeLinkProperties == null) {
                return ipAddresses;
            }

            for (LinkAddress linkAddress : activeLinkProperties.getLinkAddresses()) {
                // Log link address
                Log.d(TAG, "linkAddress: " + linkAddress);
                Log.d(TAG, "Checking IP: " + linkAddress.getAddress().getHostAddress());
                logInterestingLinkAddressFlags(TAG, linkAddress);
                // Check if the IP address is not a loopback
                if (linkAddress.getAddress().isLoopbackAddress()) {
                    Log.d(TAG, linkAddress.getAddress().getHostAddress() + " is a loopback address, skipping...");
                    continue;
                }
                // Check if the IP address is not a link-local address
                if (linkAddress.getAddress().isLinkLocalAddress()) {
                    Log.d(TAG, linkAddress.getAddress().getHostAddress() + " is a link-local address, skipping...");
                    continue;
                }
                Log.d(TAG, linkAddress.getAddress().getHostAddress() + " is a valid address, adding...");
                ipAddresses.add(linkAddress.getAddress().getHostAddress());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting IPs: " + e.getMessage());
        }

        return ipAddresses;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void logInterestingLinkAddressFlags(String tag, LinkAddress linkAddress) {
        int flags = linkAddress.getFlags();
        Log.d(tag, "IFA_F_DEPRECATED: " + ((flags & OsConstants.IFA_F_DEPRECATED) == OsConstants.IFA_F_DEPRECATED));
        Log.d(tag, "IFA_F_TEMPORARY: " + ((flags & OsConstants.IFA_F_TEMPORARY) == OsConstants.IFA_F_TEMPORARY));
        Log.d(tag, "IFA_F_HOMEADDRESS: " + ((flags & OsConstants.IFA_F_HOMEADDRESS) == OsConstants.IFA_F_HOMEADDRESS));
        Log.d(tag, "IFA_F_NODAD: " + ((flags & OsConstants.IFA_F_NODAD) == OsConstants.IFA_F_NODAD));
        Log.d(tag, "IFA_F_OPTIMISTIC: " + ((flags & OsConstants.IFA_F_OPTIMISTIC) == OsConstants.IFA_F_OPTIMISTIC));
        Log.d(tag, "IFA_F_TENTATIVE: " + ((flags & OsConstants.IFA_F_TENTATIVE) == OsConstants.IFA_F_TENTATIVE));
        Log.d(tag, "IFA_F_PERMANENT: " + ((flags & OsConstants.IFA_F_PERMANENT) == OsConstants.IFA_F_PERMANENT));
        Log.d(tag, "IFA_F_DADFAILED: " + ((flags & OsConstants.IFA_F_DADFAILED) == OsConstants.IFA_F_DADFAILED));
    }

    private void showIpsDialog(String title, List<String> ipAddresses) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        // Format the IPs to be more readable
        StringBuilder sb = new StringBuilder();
        for (String ipAddress : ipAddresses) {
            sb.append(ipAddress).append("\n");
        }
        // If the IPs list is empty then show a message
        if (sb.length() == 0) {
            sb.append("No IPs found!");
        }
        builder.setMessage(sb.toString());
        builder.setPositiveButton("OK", null);
        builder.show();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private static class NetworkMonitor {
        private final NetworkChangeListener listener;
        private ConnectivityManager.NetworkCallback networkCallback;

        public NetworkMonitor(
                NetworkChangeListener listener) {
            this.listener = listener;
        }

        private void start(Context context) throws InterruptedException {
            final CountDownLatch setNetworkPropertiesCountDownLatch = new CountDownLatch(1);

            ConnectivityManager connectivityManager =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager == null) {
                return;
            }
            networkCallback = new ConnectivityManager.NetworkCallback() {
                private Network currentActiveNetwork;

                private void consumeActiveNetwork(Network network) {
                    if (!network.equals(currentActiveNetwork)) {
                        if (listener != null) {
                            listener.onChanged(network);
                        }
                        currentActiveNetwork = network;
                    }
                    setNetworkPropertiesCountDownLatch.countDown();
                }

                private void consumeLostNetwork(Network network) {
                    if (network.equals(currentActiveNetwork)) {
                        if (listener != null) {
                            listener.onChanged(null);
                        }
                        currentActiveNetwork = null;
                    }
                    setNetworkPropertiesCountDownLatch.countDown();
                }

                @Override
                public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities capabilities) {
                    super.onCapabilitiesChanged(network, capabilities);

                    // Need API 23(M)+ for NET_CAPABILITY_VALIDATED
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                        return;
                    }

                    // https://developer.android.com/reference/android/net/NetworkCapabilities#NET_CAPABILITY_VALIDATED
                    // Indicates that connectivity on this network was successfully validated.
                    // For example, for a network with NET_CAPABILITY_INTERNET, it means that Internet connectivity was
                    // successfully detected.
                    if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                        consumeActiveNetwork(network);
                    }
                }

                @Override
                public void onAvailable(@NonNull Network network) {
                    super.onAvailable(network);

                    // Skip on API 26(O)+ because onAvailable is guaranteed to be followed by
                    // onCapabilitiesChanged
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        return;
                    }
                    consumeActiveNetwork(network);
                }

                @Override
                public void onLost(@NonNull Network network) {
                    super.onLost(network);
                    consumeLostNetwork(network);
                }
            };

            try {
                // When searching for a network to satisfy a request, all capabilities requested must be satisfied.
                NetworkRequest.Builder builder = new NetworkRequest.Builder()
                        // Indicates that this network should be able to reach the internet.
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);

                if (isVpnMode) {
                    // If we are in the VPN mode then ensure we monitor only the VPN's underlying
                    // active networks and not self.
                    builder.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN);
                } else {
                    // If we are NOT in the VPN mode then monitor default active networks with the
                    // Internet capability, including VPN, to ensure we won't trigger a reconnect in
                    // case the VPN is up while the system switches the underlying network.

                    // Limitation: for Psiphon Library apps running over Psiphon VPN, or other VPNs
                    // with a similar architecture, it may be better to trigger a reconnect when
                    // the underlying physical network changes. When the underlying network
                    // changes, Psiphon VPN will remain up and reconnect its own tunnel. For the
                    // Psiphon app, this monitoring will detect no change. However, the Psiphon
                    // app's tunnel may be lost, and, without network change detection, initiating
                    // a reconnect will be delayed. For example, if the Psiphon app's tunnel is
                    // using QUIC, the Psiphon VPN will tunnel that traffic over udpgw. When
                    // Psiphon VPN reconnects, the egress source address of that UDP flow will
                    // change -- getting either a different source IP if the Psiphon server
                    // changes, or a different source port even if the same server -- and the QUIC
                    // server will drop the packets. The Psiphon app will initiate a reconnect only
                    // after a SSH keep alive probes timeout or a QUIC timeout.
                    //
                    // TODO: Add a second ConnectivityManager/NetworkRequest instance to monitor
                    // for underlying physical network changes while any VPN remains up.

                    builder.removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN);
                }

                NetworkRequest networkRequest = builder.build();
                // We are using requestNetwork and not registerNetworkCallback here because we found
                // that the callbacks from requestNetwork are more accurate in terms of tracking
                // currently active network. Another alternative to use for tracking active network
                // would be registerDefaultNetworkCallback but a) it needs API >= 24 and b) doesn't
                // provide a way to set up monitoring of underlying networks only when VPN transport
                // is also active.
                connectivityManager.requestNetwork(networkRequest, networkCallback);
            } catch (RuntimeException ignored) {
                // Could be a security exception or any other runtime exception on customized firmwares.
                networkCallback = null;
            }
            // We are going to wait up to one second for the network callback to populate
            // active network properties before returning.
            setNetworkPropertiesCountDownLatch.await(1, TimeUnit.SECONDS);
        }

        private void stop(Context context) {
            if (networkCallback == null) {
                return;
            }
            ConnectivityManager connectivityManager =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager == null) {
                return;
            }
            // Note: ConnectivityManager.unregisterNetworkCallback() may throw
            // "java.lang.IllegalArgumentException: NetworkCallback was not registered".
            // This scenario should be handled in the start() above but we'll add a try/catch
            // anyway to match the start's call to ConnectivityManager.registerNetworkCallback()
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (RuntimeException ignored) {
            }
            networkCallback = null;
        }

        public interface NetworkChangeListener {
            void onChanged(Network network);
        }
    }
}