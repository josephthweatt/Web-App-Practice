<!DOCTYPE html>
<html lang="en">
    <head>
        <meta charset="UTF-8">
        <title>Sign Into BigMap</title>
    </head>
    <body>
    <?php
    // error_reporting(E_ALL); // uncomment for debugging
    include 'MemberUtilities.php';

    $con = mysqli_connect("localhost", "root");

    isset($_POST["userInfo"]) ? $userInfo = $_POST["userInfo"] : die ("no user id specified");
    isset($_POST["channelId"]) ? $channelId = $_POST["channelId"] : die ("channel ids not specified");

    userExists($userInfo) ? $userId = getUserId($userInfo) : die ();
    if (isAMember($channelId)) {
        // begin creating the page ($con currently points to bm_channel)
        echo "<h1>You have entered Channel " . $channelId . "</h1>";

        $channelMembers = getChannelMembers($channelId);
        foreach($channelMembers as $memberId) {
            // set user's location history
            echo "<fieldset><legend>" .getUsername($memberId). "</legend>";
             echo "<ul style=\"list-style-type:none\">";

            $locationHistory = getLocationHistory($memberId);
            foreach ($locationHistory as $location) {
                echo "<p>" . $location[2] . "</p>";
                echo "<p>" . $location[3] . "</p>";
                echo "<p>" . $location[4] . "</p>";
            }
             echo "</ul>";
            echo "</fieldset>";
        }
    } else {
        echo "<h1>You are not signed into this channel</h1>";
    }
    ?>
    </body>
</html>