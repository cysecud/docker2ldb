package it.uniud.mads.docker2ldb;

import it.uniud.mads.jlibbig.core.ldb.*;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Docker2Ldb {

    public static void main(String args[]) {
        try {
            System.out.println("Composed bigraph: \n" + docker2ldb("./etc/docker-compose.yml"));
        } catch (Exception e) {
            System.err.println(e);
        }
    }

    private static DirectedBigraph docker2ldb(String pathToYAML) throws Exception {
        // parsing yaml config file
        InputStream input = new FileInputStream(new File(pathToYAML));
        Yaml yaml = new Yaml();
        // declarations in the docker-compose.yml file
        Map<String, Map> o = (Map<String, Map>) yaml.load(input);
        Map<String, Map> services = o.get("services");
        Map<String, Map> networks = o.get("networks");
        Map<String, Map> volumes = o.get("volumes");

        System.out.println("YAML config file correctly loaded.");

        boolean default_net = true; // used to know if networks are used
        int net_size = 1; // number of declared networks
        int vol_size = 0;

        if (networks != null) {
            default_net = false;
            net_size = networks.size();
        }

        for (String service : services.keySet()) {
            List<String> current_vols = (List<String>) services.get(service).get("volumes");
            if (current_vols != null) {
                vol_size = Math.max(vol_size, current_vols.size());
            }
        }

        // preparing controls and empty bigraph
        List<DirectedControl> controls = new ArrayList<>();

        for (int i = 1; i <= net_size + vol_size; i++) {
            controls.add(new DirectedControl("container_" + i, true, i, 1));
        }

        DirectedSignature signature = new DirectedSignature(controls);
        DirectedBigraphBuilder cmp = new DirectedBigraphBuilder(signature);

        Root r0 = cmp.addRoot(); // root 0
        System.out.println("Added a root to the bigraph.");

        // networks
        Map<String, OuterName> net_names = new HashMap<>();
        if (default_net) {
            net_names.put("default", cmp.addAscNameOuterInterface(1, "default"));
            System.out.println("Added \"default\" network.");
        } else {
            for (String net : networks.keySet()) {
                net_names.put(net, cmp.addAscNameOuterInterface(1, net));
                System.out.println("Added \"" + net + "\" network.");
            }
        }

        //volumes
        Map<String, OuterName> vol_names = new HashMap<>();
        if (volumes != null) {
            for (String volume : volumes.keySet()) {
                vol_names.put(volume, cmp.addAscNameOuterInterface(1, volume));
                System.out.println("Added named volume \"" + volume + "\".");
            }
        }

        // save service outer names
        int locality = 1;
        Map<String, OuterName> onames = new HashMap<>();

        List<DirectedBigraph> graphs = new ArrayList<>(services.size());

        for (String service : services.keySet()) {
            cmp.addSite(r0); // add a site
            System.out.println("Added a site to the bigraph.");
            if (default_net) {
                cmp.addAscNameInnerInterface(locality, "default", net_names.get("default")); // add default net
            }
            onames.put(service, cmp.addDescNameInnerInterface(locality, service));
            cmp.addDescNameOuterInterface(1, service, onames.get(service)); // expose the name
            locality++;
        }

        locality = 1; // reset counter
        for (String service : services.keySet()) { // parse every service in docker-compose file
            System.out.println("Service: " + service);
            List<String> current_nets = (List<String>) services.get(service).get("networks");
            List<String> current_vols = (List<String>) services.get(service).get("volumes");
            List<String> ports = (List<String>) services.get(service).get("expose");
            List<String> mappings = (List<String>) services.get(service).get("ports");
            List<String> links = (List<String>) services.get(service).get("links");

            DirectedBigraphBuilder current = new DirectedBigraphBuilder(signature);
            System.out.println("Creating a bigraph for the service.");
            Root currentRoot = current.addRoot(); // add a root
            Node node;
            int outPorts = 0; // determine how many ports to allocate
            int current_net_size;
            if (current_vols != null) {
                outPorts += current_vols.size();
            }
            if (default_net) {
                outPorts++;
                current_net_size = 1;
            } else {
                if (current_nets != null) {
                    outPorts += current_nets.size();
                    current_net_size = current_nets.size();
                } else {
                    throw new Exception("You must declare networks service connects to, because you declared global networks!");
                }
            }
            node = current.addNode("container_" + outPorts, currentRoot); // add a node of container type

            current.addSite(node); // add a site for future purposes
            current.addDescNameOuterInterface(1, service, node.getInPort(0).getEditable());

            // networks
            if (default_net) {
                System.out.println("Service connects to network \"default\", adding it to the interface.");
                node.getOutPort(0).getEditable().setHandle(current.addAscNameOuterInterface(1, "default").getEditable()); // link the net to the node
            } else {
                int i = 0;
                // local_nets cannot be null because previous exception was skipped
                for (String network : current_nets) {
                    if (!networks.keySet().contains(network)) {
                        throw new Exception("Network \"" + network + "\" not declared.");
                    }
                    System.out.println("Service connects to network \"" + network + "\", adding it to the interface.");
                    node.getOutPort(i).getEditable().setHandle(current.addAscNameOuterInterface(1, network).getEditable()); // link the net to the node
                    cmp.addAscNameInnerInterface(locality, network, net_names.get(network));
                    i++;
                }
            }
            //volumes
            if (current_vols != null) {
                int i = 0;
                for (String volume : current_vols) {
                    String[] vs = volume.split(":");
                    if (vs.length > 1) { // check if the volume must be generated
                        if (!vs[0].startsWith("/") && !vs[0].startsWith("./") && !vs[0].startsWith("~/") && (volumes == null || !volumes.keySet().contains(vs[0]))) {
                            throw new Exception("Volume \"" + vs[0] + "\" not declared.");
                        }
                        System.out.println("Service mounts volume \"" + vs[0] + "\" at path \"" + vs[1] + "\", adding it to the interface.");
                        if (!vol_names.containsKey(vs[0])) {
                            vol_names.put(vs[0], cmp.addAscNameOuterInterface(1, vs[0]));
                        }
                        cmp.addAscNameInnerInterface(locality, vs[1], vol_names.get(vs[0]));

                        node.getOutPort(current_net_size + i).getEditable().setHandle(current.addAscNameOuterInterface(1, vs[1]).getEditable()); // link the volume to the node
                    } else {
                        System.out.println("Service mounts volume at path \"" + vs[0] + "\", adding it to the interface.");
                        cmp.addAscNameInnerInterface(locality, vs[0], cmp.addAscNameOuterInterface(1, locality + "_" + volume));

                        node.getOutPort(current_net_size + i).getEditable().setHandle(current.addAscNameOuterInterface(1, vs[0]).getEditable()); // link the volume to the node
                    }
                    i++;
                }
            }
            // expose
            if (ports != null) {
                for (String port : ports) {
                    System.out.println("Service exposes port " + port + ", adding it to the interface.");
                    current.addDescNameOuterInterface(1, port, current.addDescNameInnerInterface(1, port));
                    cmp.addDescNameInnerInterface(locality, port);
                }
            }
            // ports
            if (mappings != null) {
                for (String map : mappings) {
                    String[] ps = map.split(":");
                    System.out.println("Service maps port " + ps[1] + " to port " + ps[0] + ", adding them to interfaces.");
                    current.addDescNameOuterInterface(1, ps[1], current.addDescNameInnerInterface(1, ps[1]));
                    cmp.addDescNameOuterInterface(1, ps[0], cmp.addDescNameInnerInterface(locality, ps[1]));
                }
            }
            // links
            if (links != null) {
                for (String link : links) {
                    String[] ls = link.split(":");
                    if (ls.length > 1) {
                        System.out.println("Service links to container \"" + ls[0] + "\", renaming it to \"" + ls[1] + "\" recreating this on interfaces.");
                        current.addAscNameInnerInterface(1, "l_" + ls[1] + "_" + service, current.addAscNameOuterInterface(1, "l_" + ls[1] + "_" + service));
                        cmp.addAscNameInnerInterface(locality, "l_" + ls[1] + "_" + service, onames.get(ls[0]));
                    } else {
                        System.out.println("Service links to container \"" + ls[0] + "\", recreating this on interfaces.");
                        current.addAscNameInnerInterface(1, "l_" + ls[0] + "_" + service, current.addAscNameOuterInterface(1, "l_" + ls[0] + "_" + service));
                        cmp.addAscNameInnerInterface(locality, "l_" + ls[0] + "_" + service, onames.get(ls[0]));
                    }
                }
            }
            System.out.println("Resulting bigraph: \n" + current);
            System.out.println("----------------------------------------------");
            graphs.add(current.makeBigraph());
            locality++; // ready for the next
        }
        System.out.println("Compose bigraph: \n" + cmp);
        System.out.println("----------------------------------------------");

        List<DirectedBigraph> outs = new ArrayList<>();
        outs.add(cmp.makeBigraph());

        return DirectedBigraph.compose(outs, graphs);
    }
}
