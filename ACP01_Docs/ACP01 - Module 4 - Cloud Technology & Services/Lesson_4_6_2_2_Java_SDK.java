import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2AsyncClient;
import software.amazon.awssdk.services.ec2.model.AuthorizeSecurityGroupIngressRequest;
import software.amazon.awssdk.services.ec2.model.AuthorizeSecurityGroupIngressResponse;
import software.amazon.awssdk.services.ec2.model.CreateSecurityGroupRequest;
import software.amazon.awssdk.services.ec2.model.CreateSecurityGroupResponse;
import software.amazon.awssdk.services.ec2.model.RunInstancesRequest;
import software.amazon.awssdk.services.ec2.model.RunInstancesResponse;

Ec2AsyncClient ec2Client = Ec2AsyncClient.builder()
    .region(Region.of("eu-west-2"))
    .build();

CreateSecurityGroupRequest request1 = CreateSecurityGroupRequest.builder()
    .groupName("launch-wizard-6")
    .description("launch-wizard-6 created 2026-08-10T09:53:05.728Z")
    .vpcId("vpc-062868cd8a4b87505")
    .build();

CompletableFuture<CreateSecurityGroupResponse> response1 = ec2Client.createSecurityGroup(request1);

AuthorizeSecurityGroupIngressRequest request2 = AuthorizeSecurityGroupIngressRequest.builder()
    .groupId("sg-preview-1")
    .ipPermissions(Arrays.asList(IpPermissions.builder()
            .ipProtocol("tcp")
            .fromPort(22)
            .toPort(22)
            .ipRanges(Arrays.asList(IpRanges.builder()
            .cidrIp("82.100.100.129/32")
            .build()))
            .build()))
    .build();

CompletableFuture<AuthorizeSecurityGroupIngressResponse> response2 = response1.thenCompose(r -> ec2Client.authorizeSecurityGroupIngress(request2));

RunInstancesRequest request3 = RunInstancesRequest.builder()
    .maxCount(1)
    .minCount(1)
    .imageId("ami-01c952cfc86b7870d")
    .instanceType("t3.micro")
    .keyName("DemoWalkthroughEC2InstanceKeyPair")
    .ebsOptimized(true)
    .networkInterfaces(Arrays.asList(NetworkInterfaces.builder()
            .associatePublicIpAddress(true)
            .deviceIndex(0)
            .groups(Arrays.asList("sg-preview-1"))
            .build()))
    .creditSpecification(CreditSpecification.builder()
            .cpuCredits("unlimited")
            .build())
    .tagSpecifications(Arrays.asList(TagSpecifications.builder()
            .resourceType("instance")
            .tags(Arrays.asList(Tags.builder()
            .key("Name")
            .value("DemoWalkthroughEC2Instance")
            .build()))
            .build()))
    .metadataOptions(MetadataOptions.builder()
            .httpEndpoint("enabled")
            .httpPutResponseHopLimit(2)
            .httpTokens("required")
            .build())
    .privateDnsNameOptions(PrivateDnsNameOptions.builder()
            .hostnameType("ip-name")
            .enableResourceNameDnsARecord(true)
            .enableResourceNameDnsAaaaRecord(false)
            .build())
    .build();

CompletableFuture<RunInstancesResponse> response3 = response2.thenCompose(r -> ec2Client.runInstances(request3));
