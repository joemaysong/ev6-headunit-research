/** Apply projection stop intent; Bluetooth reconnection policy remains in OEM code. */
public final class ProjectionStopPolicy {
 public static void apply(ConnectionPolicy policy,boolean user){if(policy==null)throw new IllegalArgumentException();if(user)policy.stopByUser();else policy.disconnecting=true;}
}
