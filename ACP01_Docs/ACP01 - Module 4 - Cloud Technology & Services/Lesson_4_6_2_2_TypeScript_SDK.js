import { EC2Client, AuthorizeSecurityGroupIngressCommand, CreateSecurityGroupCommand, RunInstancesCommand } from "@aws-sdk/client-ec2";

const client = new EC2Client({ region: "eu-west-2" });

const main = async () => {
  await client.send(new CreateSecurityGroupCommand({
      GroupName: "launch-wizard-6",
      Description: "launch-wizard-6 created 2026-08-10T09:53:05.728Z",
      VpcId: "vpc-062868cd8a4b87505",
    }));

  await client.send(new AuthorizeSecurityGroupIngressCommand({
      GroupId: "sg-preview-1",
      IpPermissions: [{
        IpProtocol: "tcp",
        FromPort: 22,
        ToPort: 22,
        IpRanges: [{
          CidrIp: "82.100.100.129/32",
        }],
      }],
    }));

  await client.send(new RunInstancesCommand({
      MaxCount: 1,
      MinCount: 1,
      ImageId: "ami-01c952cfc86b7870d",
      InstanceType: "t3.micro",
      KeyName: "DemoWalkthroughEC2InstanceKeyPair",
      EbsOptimized: true,
      NetworkInterfaces: [{
        AssociatePublicIpAddress: true,
        DeviceIndex: 0,
        Groups: ["sg-preview-1"],
      }],
      CreditSpecification: {
        CpuCredits: "unlimited",
      },
      TagSpecifications: [{
        ResourceType: "instance",
        Tags: [{
          Key: "Name",
          Value: "DemoWalkthroughEC2Instance",
        }],
      }],
      MetadataOptions: {
        HttpEndpoint: "enabled",
        HttpPutResponseHopLimit: 2,
        HttpTokens: "required",
      },
      PrivateDnsNameOptions: {
        HostnameType: "ip-name",
        EnableResourceNameDnsARecord: true,
        EnableResourceNameDnsAAAARecord: false,
      },
    }));
};

main();
