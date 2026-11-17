package org.Azkerash.create_bioships.creature.digestion;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class DigestiveNetworkTest {
  private static DigestiveNetwork.Cell cell(int x,int y,int z) {return new DigestiveNetwork.Cell(x,y,z);}

  @Test void facesConnectButEdgesCornersAndGapsDoNot() {
    var network=new DigestiveNetwork<>(Map.of(cell(0,0,0),"anchor",cell(1,0,0),"face",
        cell(0,1,1),"edge",cell(-1,-1,-1),"corner",cell(4,0,0),"gap"));
    assertEquals(Set.of("anchor","face"),new HashSet<>(network.members(cell(0,0,0))));
    assertEquals(4,network.groups().size());
  }

  @Test void oneConnectionActivatesAnEntireChainOnly() {
    var network=new DigestiveNetwork<>(Map.of(cell(0,0,0),"mouth",cell(0,1,0),"middle",
        cell(0,2,0),"end",cell(0,4,0),"isolated"));
    assertTrue(network.hasConnection(cell(0,2,0),"mouth"::equals));
    assertFalse(network.hasConnection(cell(0,4,0),"mouth"::equals));
    assertFalse(network.hasConnection(cell(20,0,0),ignored->true));
  }

  @Test void aSolidCubeHasAllTwentySevenIndependentOwners() {
    var nodes=new HashMap<DigestiveNetwork.Cell,String>();
    for(int x=0;x<3;x++)for(int y=0;y<3;y++)for(int z=0;z<3;z++)nodes.put(cell(x,y,z),x+":"+y+":"+z);
    var network=new DigestiveNetwork<>(nodes);
    assertEquals(1,network.groups().size());
    assertEquals(27,new HashSet<>(network.members(cell(1,1,1))).size());
    nodes.clear();
    assertEquals(27,network.members(cell(0,0,0)).size());
    assertThrows(UnsupportedOperationException.class,()->network.members(cell(0,0,0)).clear());
  }

  @Test void aBrokenLinkRebuildsAsSeparateGroups() {
    var nodes=new HashMap<DigestiveNetwork.Cell,String>();
    nodes.put(cell(0,0,0),"mouth");nodes.put(cell(1,0,0),"link");nodes.put(cell(2,0,0),"other");
    assertTrue(new DigestiveNetwork<>(nodes).hasConnection(cell(2,0,0),"mouth"::equals));
    nodes.remove(cell(1,0,0));
    assertFalse(new DigestiveNetwork<>(nodes).hasConnection(cell(2,0,0),"mouth"::equals));
  }
}
